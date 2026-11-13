# Email notification pipeline (transactional outbox)

Notification emails are not sent directly from business code. A business
transaction writes an encrypted record to the `email_outbox` table, a scheduled
relay publishes that record to Kafka, and a dedicated consumer performs the
SMTP send. This decouples SMTP availability from API latency and guarantees
that an email is only emitted for a business change that actually committed.

## Flow

```
business transaction
  └─ EmailOutboxPublisher.enqueue(...)          (same DB transaction)
       └─ email_outbox row: PENDING             (payload AES-GCM encrypted)

EmailOutboxRelayWorker (scheduled, ShedLock)
  ├─ reclaim stale PROCESSING rows
  ├─ claim due PENDING rows with FOR UPDATE SKIP LOCKED
  │    └─ short transaction: PENDING -> PROCESSING
  ├─ publish to email.notification.V2           (outside any transaction)
  │    └─ wait for broker acknowledgement
  └─ on ack: PROCESSING -> PUBLISHED
     on failure: PROCESSING -> PENDING (backoff) or FAILED (budget spent)

EmailNotificationConsumer (group email-notification-consumer-group)
  ├─ skip if eventId already in processed_events
  ├─ decrypt payload, send via MailService (SMTP)
  ├─ PROCESSING/PUBLISHED -> DELIVERED
  ├─ write processed_events marker
  ├─ acknowledge offset
  └─ after retries: mark FAILED, publish to email.notification.dlq.V2
```

## State machine

```
PENDING -> PROCESSING -> PUBLISHED -> DELIVERED
               |              |
               +-> PENDING    +-> FAILED      (publication retries spent)
               +-> FAILED                     (poison payload / delivery retries spent)
```

* `PENDING` – waiting for the relay; `next_attempt_at` controls backoff.
* `PROCESSING` – claimed by a relay instance; `locked_at` supports stale-claim recovery.
* `PUBLISHED` – Kafka acknowledged the send.
* `DELIVERED` – SMTP accepted the message.
* `FAILED` – retry budget exhausted; the row is inspectable and replayable.

All consumer-side transitions are conditional, so a fast consumer cannot be
overwritten by a late relay update.

## Delivery guarantee

Delivery is **at-least-once**:

* The relay marks a row `PUBLISHED` only after the broker acknowledges it.
* If the process dies after the acknowledgement but before the status update,
  the stale-claim sweep republishes the row after `stale-claim-timeout`.
* The consumer records `processed_events` only after a successful SMTP send and
  performs the delivery-state update first. A crash in that window can produce
  a duplicate email, but never a lost one.
* Poison payloads (undecryptable or malformed) are non-retryable and go
  straight to the DLQ.

## Payload protection

`EmailPayloadCipher` encrypts the serialized `SendMailRequest` with AES-256-GCM
before it is stored. The outbox column, the Kafka topic, and the DLQ therefore
contain only ciphertext for recipient addresses, subjects, bodies, and OTP
codes. The envelope itself carries only event metadata and the notification
type.

The key is read from `sondhan.email.payload-key`, configured as
`EMAIL_PAYLOAD_KEY`. Set a dedicated strong secret in production instead of
relying on the documented fallback to `PII_ENCRYPTION_KEY`.

## Configuration

| Property | Default | Purpose |
| --- | --- | --- |
| `kafka.topics.email-notification` | `email.notification` | Base topic name |
| `kafka.consumer.email.group-id` | `email-notification-consumer-group` | Dedicated consumer group |
| `email.outbox.consumer.concurrency` | `1` | Email consumer threads |
| `email.outbox.relay.poll-interval` | `5s` | Relay schedule |
| `email.outbox.relay.batch-size` | `25` | Rows claimed per run |
| `email.outbox.relay.max-attempts` | `10` | Publication attempt budget |
| `email.outbox.relay.initial-backoff` | `5s` | First retry delay |
| `email.outbox.relay.max-backoff` | `5m` | Retry delay ceiling |
| `email.outbox.relay.stale-claim-timeout` | `5m` | When PROCESSING rows are reclaimed |
| `email.outbox.retention` | `7d` | Terminal-row retention |
| `email.outbox.cleanup.cron` | `0 30 2 * * *` | Retention job schedule |
| `kafka.producer.publish-timeout-ms` | `10000` | Broker acknowledgement timeout |
| `sondhan.email.payload-key` | fallback to PII key | Payload encryption key |

## Operations

Inspect pending or stuck work:

```sql
SELECT status, count(*), min(created_at)
FROM email_outbox
GROUP BY status;

SELECT id, event_id, event_type, attempts, next_attempt_at, last_error
FROM email_outbox
WHERE status IN ('PENDING', 'PROCESSING', 'FAILED')
ORDER BY updated_at DESC
LIMIT 50;
```

Replay a failed message after fixing the cause. Resetting the claim keeps the
same `event_id`, so the consumer idempotency marker still suppresses a message
that was already delivered:

```sql
UPDATE email_outbox
SET status = 'PENDING',
    attempts = 0,
    next_attempt_at = now(),
    locked_at = NULL,
    last_error = NULL,
    updated_at = now()
WHERE event_id = '<event-id>';
```

Deleting terminal rows is handled by the retention job; do not delete
`PENDING`, `PROCESSING`, or `FAILED` rows without investigating them first.

Topics are created by the existing topic registry with the same version suffix
as the other events (`email.notification.V2`, `email.notification.retry.V2`,
`email.notification.dlq.V2`). Ensure the broker or provisioning process has
created them before enabling the pipeline in an environment.
