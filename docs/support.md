# Support runbook

Day-to-day support procedures. Everything here is admin-only or DB-side; none
of it requires a rebuild or a deploy.

## “User did not receive the OTP email”

The OTP pipeline is register/login → `email_outbox` row → Kafka relay → SMTP
consumer. Failures park at known places; check them in order.

1. **OTP row missing / expired.** OTPs live 180 s. If the user is slow, the
   fix is: log in or register again — either flow issues a fresh OTP. The
   API error (`OTP_EXPIRED`) already says this.
2. **Outbox row FAILED.** SMTP outages exhaust the relay/consumer retry
   budget and park rows as FAILED in `email_outbox` (`status`, `last_error`,
   `attempts` columns are readable).
   - Re-deliver everything parked: `POST /api/mail/outbox/replay-failed`
     (system admin). Rows return to PENDING with a fresh attempt budget and
     the relay picks them up on its next tick.
3. **Consumer DLQ.** If a row was marked FAILED by the consumer, the event
   was also dead-lettered to the email notification DLQ topic. Replay from
   Kafka is possible but the outbox replay above is the intended path.
4. **Dev environments** pin a fixed OTP (`123456`, in
   `application-dev.properties` only) so registration can be verified without
   a working SMTP. Never set it in production.

## “User cannot be found”

Users are located by email through a keyed hash, not a plaintext column:
`hmac-sha256(PII_HASH_KEY, email)` stored in `pii_tokens.value_hash`. Hash
the candidate email the same way and join through the token table.

Duplicate-registration attempts against an existing account return a decoy
user id and an “OTP sent” message with no OTP behind it (anti-enumeration);
verification then fails with `OTP_EXPIRED`. This is intentional — do not
“fix” the inconsistency, and do not confirm account existence to callers.

## Blood request lifecycle (v: cancel support)

States: `PENDING → ACCEPTED → PENDING_APPROVAL → FULFILLED`, with `DISPUTED`
(admin-resolved) and `EXPIRED` (TTL worker). `CANCELLED` is set only by the
requester while the request is still `PENDING`:
`POST /api/blood-request/{id}/cancel`. A request that already has a donor
cannot be cancelled — it must go through the donation flow (complete →
approve/dispute). A wedged request (e.g. PENDING with an accepted donor from
a pre-fix bug) can be corrected in the DB directly; record the change in the
audit log.

## Notification badge

The unread count is exactly the number of unread `notification` rows.
Reading a chat clears that chat's CHAT_MESSAGE notifications; lifecycle
notifications (request accepted, donation completed, …) clear only through
the notification read endpoints. A “badge went up when I read the chat”
report from before the fix is explained by the old behaviour, not a new bug.

## Kafka user events

User events (registered/updated/deleted) retry in-process and are then
dead-lettered to the per-topic DLQ. A donor missing from search results is
usually a lost/failed `user.registered` indexing: the ES document self-heals
on the donor's next location update, or the event can be replayed from the
DLQ topic.

## JWT lifetime

JWTs are signed with ephemeral RSA keys: every restart/rebuild invalidates
all outstanding tokens and users must log in again. A wave of “everyone got
logged out” after a deploy is expected; a private/public key pair will be
provided later to make tokens survive restarts.

## Blood-request limits

A requester can have at most 3 in-flight requests
(PENDING/ACCEPTED/PENDING_APPROVAL/DISPUTED). The 4th creation attempt is
rejected with HTTP 429: “You already have N blood request(s) in flight”.
Adjust with `app.request.max-active-per-requester` if a legitimate user (e.g.
a family coordinator) needs more. Cancelling a PENDING request frees the slot
immediately.

## Resend OTP

`POST /api/auth/resend-otp` answers “OTP sent” with 200 for every request —
including unknown user ids. Nothing is delivered to an unknown id; this is
deliberate anti-enumeration, so do not “fix” the 200. Real resends share the
verification rules (lockout, purpose restrictions) and the 10/hour OTP cap
per email/phone still applies.
