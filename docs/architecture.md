# Sondhan — System Architecture and Dataflow

> **Living document.** This file is the high-level map of the service. Update it
> in the same change that alters a component, data store, API surface, event
> flow, or deployment topology. See
> [Keeping this document current](#16-keeping-this-document-current).

Sondhan is a Spring Boot backend for finding nearby blood donors. It handles
donor registration, OTP verification, JWT authentication, donor search,
blood-request creation, urgent-request escalation, request acceptance with
private chat, donation completion, document verification, a notification center
with a daily email digest, audit logging, and an event-driven search read model.

The short version:

* **PostgreSQL is the source of truth.** All business state, PII tokens, OTPs,
  outbox rows, idempotency markers and audit records live there.
* **Kafka carries domain events** for search indexing, geo/location sync and
  email delivery. Topics are versioned, with retry and dead-letter variants.
* **Redis holds ephemeral state** — token whitelisting, OTP counters, auth
  failure counters, geocoding cache, chat presence and WebSocket fanout — and
  is a clustered cache, not a source of truth.
* **Elasticsearch is a derived read model** for registered donors, including a
  `geoUpdatedAt` freshness stamp and plain `latitude`/`longitude` fields used by
  the expanding-radius nearby search. It can be rebuilt from PostgreSQL and
  `user.registered` events, but there is no automated reindex job yet.
* **Email is sent through a transactional outbox**, never directly from a
  business transaction. Product events are not emailed one by one; a daily
  digest worker is the only product-email producer.
* **Realtime chat is REST-first + STOMP push.** Messages are persisted over
  REST, then pushed to `/user/queue/chat` via a Redis pub/sub fanout so any
  application instance can reach the recipient.
* **Geo enrichment and location freshness are asynchronous**, with scheduled
  workers, claim-based locking and bounded retries.
* **Documents live in MinIO**, an S3-compatible object store; the API only
  issues short-lived presigned URLs.

---

## 1. System context

```mermaid
flowchart LR
    donor[Donor / blood requester]
    admin[Org admin / system admin]
    client[Web or mobile client]
    api[Sondhan API<br/>Spring Boot]
    smtp[SMTP provider]
    geocoder[Nominatim geocoder]
    logs[Logstash / Kibana<br/>optional]

    donor --> client
    admin --> client
    client -->|"HTTPS / JSON / JWT"| api
    api -->|"OTP and donor-alert email"| smtp
    api -->|"city to latitude/longitude"| geocoder
    api -->|"structured application logs"| logs
```

The API is stateless from the client's point of view: clients authenticate with
a bearer token and never rely on a server-side HTTP session. The Spring Session
dependency is on the classpath, but no session store is configured or enabled;
the JWT filter and `STATELESS` policy govern request authentication.

---

## 2. Container and runtime view

```mermaid
flowchart TB
    subgraph runtime[Sondhan runtime]
        app["Spring Boot application<br/>REST API · STOMP/WS · Kafka consumers · scheduled workers"]
        pg[("PostgreSQL<br/>source of truth · Flyway")]
        redis[("Redis cluster<br/>tokens · OTP state · cache · chat presence/fanout")]
        es[("Elasticsearch<br/>registered_donors read model")]
        kafka[("Kafka<br/>domain and email events")]
        minio[("MinIO / S3<br/>documents and profile photos")]
    end

    smtp[SMTP provider]
    geocoder[Nominatim geocoder]
    firebase[Firebase Cloud Messaging]

    app -->|"JPA / transactions"| pg
    app -->|"Lettuce"| redis
    app -->|"Spring Data Elasticsearch"| es
    app -->|"produce / consume"| kafka
    app -->|"S3 API / presigned URLs"| minio
    app -->|"STARTTLS"| smtp
    app -->|"HTTPS, cached in Redis"| geocoder
    app -->|"HTTP v1 push (optional)"| firebase
```

Runtime characteristics:

| Concern | Behaviour |
| --- | --- |
| HTTP port / context path | `8088`, `/sondhan` |
| API docs | Swagger UI at `/sondhan/swagger-ui/index.html` (disable with `SPRINGDOC_ENABLED=false`) |
| Health | `/sondhan/actuator/health`, `/sondhan/actuator/health/liveness`, `/sondhan/actuator/health/readiness` |
| Shutdown | Graceful; in-flight requests finish, consumers stop cleanly |
| Configuration | `.env` in development; environment variables / Docker secrets in deployment |
| Schema | Flyway migrations under `src/main/resources/db/migration` |
| JPA | `ddl-auto=validate`; Flyway owns the schema |

Deployment topology, Compose files and production rules are documented in
[docs/container-deployment.md](container-deployment.md).

---

## 3. Inside the application

```mermaid
flowchart LR
    request[HTTP request] --> valve["Tomcat RemoteIpValve<br/>trusted proxy IPs"]
    valve --> filter["JwtAuthenticationFilter"]
    filter -->|"public endpoint"| controller
    filter -->|"valid access token + Redis whitelist"| controller
    controller --> service["Application service"]
    service --> repository["JPA repository"]
    service --> redis[(Redis)]
    service --> es[(Elasticsearch)]
    service --> outbox[(email_outbox)]
    service --> producer["Kafka producer"]
    repository --> pg[(PostgreSQL)]
```

Package responsibilities:

| Package area | Responsibility |
| --- | --- |
| `controller` | REST endpoints and request/response mapping |
| `service` | Use cases: auth, users, search, blood requests, chat, donations, documents, notifications, OTP, mail, audit |
| `service/es` | Elasticsearch donor indexing, admin search and the expanding-radius nearby search |
| `service/chat`, `service/push` | Realtime WebSocket fanout/presence and FCM push ports |
| `storage` | Object-storage port and MinIO adapter for documents |
| `repository` | Spring Data JPA and Elasticsearch repositories |
| `kafka` | Events, topics, producers, consumers, retry/DLQ, outbox, idempotency |
| `worker` | Scheduled geo-enrichment, location-staleness, escalation, donation auto-approval, digest and outbox-relay jobs |
| `security` | JWT filter, key provider, principal, CORS, API authorizer, IP rate limiter, entry points |
| `search` | Redis GEO hot-path building blocks and search DTOs (not yet wired to an endpoint) |
| `model` / `payload` | JPA entities, Elasticsearch documents, enums, requests/responses |
| `config` | Kafka, Redis, Elasticsearch, async, cache, ShedLock, security, mail, WebSocket, MinIO |
| `exception` | Domain exceptions and the global JSON error handler |

Cross-cutting behaviour:

* **Request ID** — the log pattern reserves a `requestId` MDC field, but no
  filter currently populates it, so request correlation is not yet emitted.
  This is noted in [known gaps](#15-known-gaps-and-current-state).
* **Trusted proxy handling** is done by Tomcat's `RemoteIpValve`; application
  code never reads `X-Forwarded-For` directly. This prevents client IP
  spoofing that would defeat auth rate limits.
* **Errors** are returned as a consistent `ApiErrorResponse`. Stack traces and
  internal exception messages are never exposed to clients.
* **Audit entries** are written asynchronously in a separate transaction, so a
  failed audit insert cannot roll back a login or registration.

---

## 4. Domain data model

```mermaid
erDiagram
    APP_USER ||--o{ OTP_CODE : "receives"
    APP_USER }o--o{ ROLE : "user_roles"
    ROLE }o--o{ PERMISSION : "role_permissions"
    APP_USER }o--|| PII_TOKEN : "email_token_id"
    APP_USER }o--|| PII_TOKEN : "phone_token_id"
    APP_USER ||--o{ BLOOD_REQUEST : "creates"
    BLOOD_REQUEST ||--o{ DONATION_HISTORY : "fulfilled by"
    APP_USER ||--o{ DONATION_HISTORY : "donor / recipient"
    APP_USER ||--o{ AUDIT_LOG : "acts in"
    BLOOD_REQUEST ||--o| CHAT : "opens on accept"
    APP_USER ||--o{ CHAT : "requester / donor"
    CHAT ||--o{ CHAT_MESSAGE : "messages"
    BLOOD_REQUEST ||--o| DONATION_RECORD : "completed as"
    APP_USER ||--o{ NOTIFICATION : "receives"
    APP_USER ||--o{ PUSH_TOKEN : "registers"
    APP_USER ||--o{ DOCUMENT : "uploads"

    APP_USER {
        uuid id PK
        string first_name
        string last_name
        uuid email_token_id FK
        uuid phone_token_id FK
        string password_hash
        string blood_group
        boolean is_verified
        boolean is_active
        boolean is_available
        datetime last_donation_date
        string city
        decimal latitude
        decimal longitude
        string geo_status
        int geo_retry_count
    }
    PII_TOKEN {
        uuid id PK
        string pii_type
        bytea encrypted_value
        string value_hash
    }
    OTP_CODE {
        uuid id PK
        uuid user_id FK
        string code_hash
        string purpose
        datetime expires_at
        datetime used_at
    }
    BLOOD_REQUEST {
        uuid id PK
        uuid requester_id FK
        uuid accepted_donor_id FK
        string blood_group
        string status
        string urgency
        datetime expires_at
        int escalation_level
        string city
        decimal latitude
        decimal longitude
    }
    CHAT {
        uuid id PK
        uuid request_id FK
        uuid requester_id FK
        uuid donor_id FK
        string status
    }
    CHAT_MESSAGE {
        uuid id PK
        uuid chat_id FK
        uuid sender_id FK
        string body
        datetime read_at
    }
    DONATION_RECORD {
        uuid id PK
        uuid blood_request_id FK
        uuid donor_id FK
        uuid requester_id FK
        int rating
    }
    NOTIFICATION {
        uuid id PK
        uuid recipient_id FK
        string type
        jsonb payload
        uuid reference_id
        datetime read_at
        datetime email_digest_at
    }
    DOCUMENT {
        uuid id PK
        uuid owner_id FK
        string doc_type
        string bucket
        string object_key
        string checksum_sha256
        string verification_status
    }
    AUDIT_LOG {
        uuid id
        uuid user_id
        string event
        string ip_address
        jsonb metadata
        datetime created_at
    }
```

### PostgreSQL tables

| Table | Purpose |
| --- | --- |
| `app_user` | Donor / requester account, hashed password, status flags, embedded geo location and geo-enrichment state |
| `pii_tokens` | Encrypted email / phone values plus a keyed lookup hash |
| `roles`, `permissions`, `user_roles`, `role_permissions` | Role-based access control |
| `otp_codes` | Single-use OTPs: server-side HMAC hash plus a per-code random salt |
| `blood_request` | Blood requests, urgency/expiry/escalation state and the accepted donor |
| `chat`, `chat_message` | One private chat per accepted request and its messages |
| `notification` | In-app notification feed + realtime badge source (also the daily-digest source) |
| `push_token` | Registered FCM device tokens per user |
| `document` | Uploaded profile photos / medical reports with MinIO keys and verification state |
| `donation_record` | Immutable record of each completed donation (audit + donor history) |
| `donation_history` | Recorded donations between donor, recipient and request |
| `audit_logs` | Security and account events, range-partitioned by `created_at` |
| `processed_events` | Consumer idempotency markers keyed by Kafka `eventId` |
| `email_outbox` | Transactional outbox for email notifications |
| `shedlock` | Distributed scheduler locks for multi-instance deployments |
| `event_tracker` | Legacy event-tracking table; the current consumer path uses `processed_events` |
| `flyway_schema_history` | Applied migration history |

### Elasticsearch

| Index | Written by | Read by | Content |
| --- | --- | --- | --- |
| `registered_donors` | `UserRegisteredEventHandler`, `GeoEnrichedEventHandler`, `UserLocationUpdatedEventHandler` | Admin Elasticsearch endpoints and nearby donor search | Donor profile for search, including geo point, `geoUpdatedAt` freshness stamp and PII **token ids** (never raw email/phone) |

`donor_search_index` is referenced by the admin paginated-search repository
configuration. See [known gaps](#15-known-gaps-and-current-state) for the index
alignment caveat.

### Redis key families

| Key family | Purpose | TTL / notes |
| --- | --- | --- |
| `auth:whitelist:access:<jti>` | Access-token allow-list | Remaining token lifetime |
| `auth:whitelist:refresh:<jti>` | Refresh-token allow-list | Remaining token lifetime |
| `auth:user:access:<userId>`, `auth:user:refresh:<userId>` | Per-user token index for bulk revocation | Longest token lifetime |
| `auth:failure:<sha256(scope|ip|subject)>` | Login / OTP failure counters | Configured window |
| `otp:rl:<sha256(recipient)>` | OTP request rate limit | 1 hour |
| `otp:<userId>:<purpose>` | OTP state marker | OTP lifetime |
| `sondhan::<cache>::<key>` | Spring Cache entries | Per-cache TTL |
| `ws:presence:<userId>` | Owning WebSocket session id per online user | `app.chat.presence.ttl-seconds` (default 300 s) |
| `loc:throttle:<userId>` | Per-user location-update throttle | `app.location.update.min-interval-seconds` |
| `geo:donors:<bloodGroup>` | Reserved Redis GEO donor index | Not currently written by the active search path |
| `search:rl:<userId>` | Reserved Redis GEO search rate limit | 1 minute |

A Redis pub/sub channel (not a key family) — `sondhan:ws:fanout` — carries
WebSocket fanout envelopes to every application instance. See
[section 11.4](#114-request-accept-and-realtime-chat).

Redis Cluster is used in Compose (3 masters, 3 replicas). Multi-key operations
are avoided deliberately because keys can live in different hash slots.

---

## 5. API surface

All paths are relative to `/sondhan`. Authentication is a bearer access token
unless the endpoint is marked public.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | Public | Create an unverified account and send a registration OTP |
| `POST` | `/api/auth/verify-otp` | Public | Verify registration or login OTP; may return a token pair |
| `POST` | `/api/auth/resend-otp` | Public | Re-issue an OTP for a supported purpose |
| `POST` | `/api/auth/login` | Public | Password login; returns access + refresh tokens |
| `POST` | `/api/auth/refresh` | Public (refresh token) | Rotate a refresh token into a new token pair |
| `POST` | `/api/auth/logout` | Public (tokens supplied) | Revoke the supplied access and refresh tokens |
| `GET` | `/api/auth/jwks.json` | Public | Publish the RSA public key as a JWK Set |
| `PUT` | `/api/user/me` | Authenticated | Update the caller's own profile |
| `POST` | `/api/user/update` | Authenticated | Backwards-compatible alias for `/me` (ignores any client-supplied user id) |
| `POST` | `/api/user/location` | Authenticated | Publish a fresh device location (server-side throttle, Kafka fanout to ES) |
| `POST` | `/api/blood-request/create` | Authenticated | Create a blood request owned by the caller |
| `POST` | `/api/blood-request/{id}/accept` | `DONOR`, `ORG_ADMIN`, `SYSTEM_ADMIN` | Atomically accept an open request and open the private chat |
| `POST` | `/api/search/donors` | Authenticated | Elasticsearch donor page search |
| `POST` | `/api/search/donors/nearby` | Authenticated | Expanding-radius nearby search (5→80 km, `search_after` pagination) |
| `POST` | `/api/search/eligible-donors` | Authenticated | Find nearby donors who pass the eligibility filter |
| `POST` | `/api/search/notify-near-by-donors` | `SYSTEM_ADMIN`, `ORG_ADMIN` | Queue donor-alert emails for nearby eligible donors |
| `GET` | `/api/chat` | Authenticated (participant) | List the caller's chats |
| `GET` | `/api/chat/{chatId}/messages` | Authenticated (participant) | Keyset message backfill |
| `POST` | `/api/chat/{chatId}/messages` | Authenticated (participant) | Persist a message, then fan out over STOMP |
| `POST` | `/api/chat/{chatId}/read` | Authenticated (participant) | Mark a chat read |
| `GET` | `/api/notifications` | Authenticated | Notification feed (keyset) |
| `GET` | `/api/notifications/unread-count` | Authenticated | Badge count |
| `POST` | `/api/notifications/{id}/read` | Authenticated | Mark one notification read |
| `POST` | `/api/notifications/read-all` | Authenticated | Mark every notification read |
| `POST` | `/api/push/tokens` | Authenticated | Register an FCM device token |
| `DELETE` | `/api/push/tokens/{token}` | Authenticated | Remove a device token |
| `POST` | `/api/documents` | Authenticated | Upload a profile photo or report to MinIO (`multipart/form-data`) |
| `GET` | `/api/documents` | Authenticated | List the caller's documents |
| `GET` | `/api/documents/{id}` | Authenticated | Document metadata |
| `GET` | `/api/documents/{id}/download-url` | Authenticated | Short-lived presigned download URL |
| `DELETE` | `/api/documents/{id}` | Authenticated | Delete a document and its object |
| `POST` | `/api/donations/{requestId}/complete` | `DONOR` | Donor marks the donation complete |
| `POST` | `/api/donations/{requestId}/approve` | Authenticated (requester) | Approve, optionally rate 1–5 |
| `POST` | `/api/donations/{requestId}/dispute` | Authenticated (requester) | Dispute a completion claim |
| `GET` | `/api/donations/stats/{donorId}` | Authenticated | Public donor stats (count, rating) |
| `GET` | `/api/donations/my` | Authenticated | The caller's donations |
| `POST` | `/api/mail/send` | `SYSTEM_ADMIN`, `ORG_ADMIN` | Send a mail through the mail service |
| `POST` | `/es/users/save` | `SYSTEM_ADMIN`, `ORG_ADMIN` | Administrative donor index write |
| `GET` | `/es/users/search_by_bloodgroup` | `SYSTEM_ADMIN`, `ORG_ADMIN` | Administrative donor search by blood group |
| `POST` | `/es/users/search-user` | `SYSTEM_ADMIN`, `ORG_ADMIN` | Administrative paginated donor search |
| `GET` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Public | Container / orchestrator probes |
| `GET` | `/actuator/**` (other) | `SYSTEM_ADMIN`, `ORG_ADMIN` | Actuator endpoints; only health/info are exposed |

WebSocket: STOMP clients connect to `/ws` (token supplied in the CONNECT frame)
and subscribe to `/user/queue/chat`, `/user/queue/chat-events` and
`/user/queue/notifications`.

---

## 6. Authentication and authorization

Authentication uses **RS256 JWTs** with a public JWKS endpoint. Access tokens
are short-lived (`1200 s` by default); refresh tokens are long-lived
(`604800 s`). Every issued token is also recorded in Redis by `jti`. A token is
accepted only when its signature, issuer, expiry, type and Redis whitelist entry
are all valid.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant A as AuthController
    participant S as SondhanAuthService
    participant U as UserServiceImpl
    participant O as OtpService / Issuance
    participant P as EmailOutboxPublisher
    participant D as PostgreSQL
    participant R as Redis
    participant K as Kafka
    participant E as Elasticsearch

    C->>A: POST /api/auth/register
    A->>S: register(request, clientContext)
    S->>U: registerNewUser(...)
    U->>D: find or create PII tokens
    U->>D: INSERT app_user (inactive, unverified)
    U->>O: issue REGISTER OTP
    O->>D: INSERT otp_codes (hashed)
    O->>R: OTP rate-limit and state keys
    O->>P: enqueue OTP email
    P->>D: INSERT email_outbox PENDING
    D-->>C: userId + TTL

    Note over D,K: After commit, the outbox relay publishes<br/>email.notification.V2 and SMTP sends the OTP.

    C->>A: POST /api/auth/verify-otp
    A->>S: verifyOtp(...)
    S->>R: check auth failure budget
    S->>D: load user, active OTP
    S->>O: verify hash, mark OTP used
    S->>U: complete registration
    U->>D: verified, active, default role, geo coordinates
    U->>K: publish user.registered.V2 after commit
    S->>R: whitelist access + refresh jti
    S-->>C: token pair
    K-->>E: index registered donor document
```

Login and refresh follow the same trust model:

1. **Login** resolves the identifier through the PII token hash, compares the
   password with BCrypt (cost 12), checks the account status, then issues and
   whitelists a token pair. Unknown accounts still run a dummy BCrypt comparison
   to reduce timing differences.
2. **Refresh** validates the signed refresh token, atomically consumes its
   Redis whitelist entry, and rotates it. Reuse of an already-consumed refresh
   token is treated as possible theft: all tokens for that user are revoked and
   an audit event is recorded.
3. **Logout** revokes both supplied tokens. Logout can parse an expired token
   for revocation purposes, but only after signature verification.

Authorization:

* Role checks use Spring method security (`@PreAuthorize`) and URL rules in
  `SecurityConfig`. The three roles are `ROLE_DONOR`, `ROLE_ORG_ADMIN` and
  `ROLE_SYSTEM_ADMIN`; the database seeds `ROLE_SYSTEM_ADMIN` and the code
  uses the `ROLE_` prefix consistently (`hasAnyRole('SYSTEM_ADMIN', …)`).
* The default role for a newly verified user is `ROLE_DONOR`.
* Administrative capabilities (mail, Elasticsearch writes, donor-alert
  broadcasts, actuator) are restricted to `ORG_ADMIN` and `SYSTEM_ADMIN`.
* Authorization is also enforced in `ApiAuthorizer` (`@authz`) and in service
  code: every write is principal-bound, so a caller can only mutate their own
  profile, documents, chat messages, notifications and push tokens.
* Public endpoints are limited to auth (register/verify/resend/login/refresh/
  logout/JWKS), health probes, and — when enabled — API docs. The search
  endpoints, WebSocket handshake and every `/api/**` business endpoint require
  a valid access token.
* WebSocket clients authenticate once at STOMP `CONNECT` with a bearer token;
  `StompAuthChannelInterceptor` verifies the JWT, checks the Redis whitelist and
  binds the principal to the session.
* CORS is an explicit allow-list from `sondhan.cors.allowed-origins`; wildcard
  origins are rejected at startup. `X-Forwarded-For` is only trusted from the
  private/loopback proxy regex in `app.proxy.trusted-ip-regex`.
* CSRF is disabled because the API is stateless and uses bearer tokens, not
  browser-managed session cookies.
* `IpRateLimitFilter` applies a coarse per-IP request budget before the auth
  filters run, and the auth service applies per-identifier failure budgets
  (below).

Brute-force controls:

| Control | Rule |
| --- | --- |
| Login failures | 10 failures per 5 minutes, scoped by client IP + resolved user |
| OTP verification failures | Same failure budget, scoped by purpose |
| OTP requests | 10 per hour per phone and per email |
| OTP verification lockout | 5 wrong attempts set an account lockout for 15 minutes |
| Per-IP budget | `IpRateLimitFilter` rejects bursts above `app.rate-limit.*` before auth |
| OTP storage | Server-side HMAC (`OTP_HASH_KEY`) plus a per-code random salt; plaintext never stored |
| Anti-enumeration | Register, resend and login return the same response whether or not the identifier exists |

Registration, OTP resend and login are deliberately **anti-enumeration**: an
unknown email/phone gets the same status, body shape and timing profile as a
known one, with a dummy BCrypt comparison on the login path. Only server-side
audit records distinguish the cases.

---

## 7. PII protection

Raw email addresses and phone numbers are **not stored on `app_user`**. The
account holds foreign keys to `pii_tokens`:

```mermaid
flowchart LR
    input["email or phone"] --> hash["HMAC-SHA256<br/>lookup hash"]
    input --> encrypt["pgcrypto encryption"]
    hash --> token[(pii_tokens.value_hash)]
    encrypt --> token
    token --> user[(app_user.email_token_id<br/>app_user.phone_token_id)]
    user --> events["Kafka events<br/>token ids only"]
    events --> es[(Elasticsearch<br/>token ids only)]
    user --> decrypt["PiiService.decrypt<br/>only for delivery"]
    decrypt --> mail["OTP / digest / admin alert email"]
```

Rules:

* Lookup uses a keyed HMAC-SHA256 hash (`PII_HASH_KEY`). A legacy unkeyed
  SHA-256 lookup is still accepted for old rows; new values always use the
  keyed hash.
* `PiiTokenizer.findOrCreate` is transactional and unique-indexed on
  `(pii_type, value_hash)`.
* `UserRegisteredEvent` and `RegisteredDonorDocument` carry **token ids**, not
  plaintext PII. Admin Elasticsearch responses hide `email` and `phone` fields
  with `WRITE_ONLY` where those fields exist.
* Email bodies, OTP codes and recipient addresses are encrypted with
  AES-256-GCM (`EmailPayloadCipher`) before entering `email_outbox`, Kafka, or
  a DLQ.
* Logs mask identifiers and email addresses; raw tokens are never logged.

Key management caveat: losing `PII_ENCRYPTION_KEY` makes existing encrypted
values unrecoverable, and changing `PII_HASH_KEY` breaks identifier lookup.
Back these keys up in a real secret manager. See
[docs/container-deployment.md](container-deployment.md#3-configuration-and-secrets).

---

## 8. Event and topic model

Topics are derived from `KafkaTopics` and versioned by `kafka.topic.version`
(currently `V2`):

| Base event | Main topic | Retry topic | Dead-letter topic |
| --- | --- | --- | --- |
| `user.registered` | `user.registered.V2` | `user.registered.retry.V2` | `user.registered.dlq.V2` |
| `user.updated` | `user.updated.V2` | `user.updated.retry.V2` | `user.updated.dlq.V2` |
| `user.geo.enriched` | `user.geo.enriched.V2` | `user.geo.enriched.retry.V2` | `user.geo.enriched.dlq.V2` |
| `user.location.updated` | `user.location.updated.V2` | `user.location.updated.retry.V2` | `user.location.updated.dlq.V2` |
| `chat.message.sent` | `chat.message.sent.V2` | `chat.message.sent.retry.V2` | `chat.message.sent.dlq.V2` |
| `email.notification` | `email.notification.V2` | `email.notification.retry.V2` | `email.notification.dlq.V2` |

`BaseEvent` carries `eventId`, `topicName`, `eventType`, `eventTimeStamp`,
`retryCount`, `version`, `aggregateId`, `causationId` and `source`.

Producer and consumer behaviour:

| Path | Behaviour |
| --- | --- |
| `GenericKafkaEventProducer.publishEvent` | Fire-and-forget; on async failure publishes to retry (up to 3) then DLQ |
| `GenericKafkaEventProducer.publishEventBlocking` | Waits for broker acknowledgement; used by the outbox relay |
| `publishToDlqBlocking` | Waits for DLQ acknowledgement; used by the email consumer error handler |
| Consumer factory | Manual immediate acknowledgement, JSON deserialization with trusted packages and type mappings, error-handling deserializer, fixed backoff (3 retries at 1 s) |
| Consumer exhaustion | After the retry budget is spent the record is logged and skipped; the generic factory has no DLQ recoverer |
| Idempotency | `processed_events` keyed by `eventId`; the marker is removed if the handler throws, so a retry can succeed |
| Email consumer | Dedicated factory, dedicated group, 1 concurrent consumer, non-retryable payload errors, explicit DLQ recoverer |

The producer is configured with `acks=all`, idempotence enabled, compression,
and a bounded in-flight request count.

---

## 9. Registration, indexing and geo enrichment

### 9.1 Registration and search indexing

`user.registered.V2` is published **after the registration transaction
commits**. This avoids indexing an account that was rolled back.

```mermaid
sequenceDiagram
    autonumber
    participant U as UserServiceImpl
    participant DB as PostgreSQL
    participant K as Kafka
    participant C as UserEventConsumer
    participant H as UserRegisteredEventHandler
    participant ES as Elasticsearch

    U->>DB: commit verified, active user + default role
    U->>K: publish user.registered.V2 after commit
    K-->>C: consume event
    C->>H: dispatch by topic
    H->>DB: claim eventId in processed_events
    H->>ES: upsert registered_donors document
    H-->>C: success
    C->>K: acknowledge offset
```

`UserRegisteredEventHandler` skips the write if a document with the same user id
already exists. A failed index write throws, the idempotency claim is
compensated, and Kafka retries the event.

### 9.2 Geo enrichment lifecycle

New accounts are created with geo status `PENDING`. OTP verification makes a
best-effort synchronous geocode of the city and stores the coordinates, but it
does not mark the row `COMPLETED`. The scheduled worker later claims the row,
geocodes the city again, and publishes `user.geo.enriched.V2`; the handler
applies the coordinates and moves the row to `COMPLETED`.

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING: worker claim
    PROCESSING --> COMPLETED: geocode succeeded and event applied
    PROCESSING --> PENDING: failure, attempts < 5
    PROCESSING --> FAILED: failure, attempts >= 5
    COMPLETED --> [*]
    FAILED --> [*]
```

```mermaid
sequenceDiagram
    autonumber
    participant W as GeoEnrichmentWorker
    participant DB as PostgreSQL
    participant G as Nominatim
    participant K as Kafka
    participant H as GeoEnrichedEventHandler
    participant ES as Elasticsearch

    W->>DB: claim batch of PENDING users (conditional UPDATE)
    Note over W,DB: claim changes PENDING -> PROCESSING,<br/>sets geo_claimed_at
    W->>G: GET /search?format=json&q=city
    G-->>W: latitude, longitude
    W->>K: publish user.geo.enriched.V2 after commit
    K-->>H: consume event
    H->>DB: update coords only if still PROCESSING
    H->>ES: partial update of location
```

Worker properties:

| Property | Default | Meaning |
| --- | --- | --- |
| `geo.enrichment.poll-interval` | `10m` | Worker schedule |
| batch size | `20` | Users processed per run |
| throttle | `1100 ms` | Pause between geocoder calls (Nominatim usage policy) |
| retry limit | `5` | Attempts before `FAILED` |
| ShedLock name | `geoEnrichmentWorker` | One active worker across instances |
| geocoder timeout | `5 s` | Per request |

If the handler sees that the row is no longer `PROCESSING`, it logs and skips
the DB update. This protects against a second worker or a late event.

`GeoEnrichedEventHandler.handle(...)` opens a transaction at the handler
boundary. The geo update is a `@Modifying` JPQL query, which requires an active
transaction; without one the consumer fails with
`TransactionRequiredException` and the event is redelivered. The idempotency
marker is written in a separate `REQUIRES_NEW` transaction by
`JpaProcessedEventStore` and compensated if the handler later fails, so the
marker and the geo update are not committed as one unit.

> `blood_request` has geo-enrichment columns, but no worker currently claims or
> enriches requests. Only `app_user` is processed today.

---

## 10. Email notification pipeline

Email is the only notification transport used by the current code. Business
code never calls SMTP directly; it writes to `email_outbox` in the same
transaction.

```mermaid
sequenceDiagram
    autonumber
    participant B as Business service
    participant DB as email_outbox
    participant R as EmailOutboxRelayWorker
    participant K as Kafka
    participant C as EmailNotificationConsumer
    participant S as SMTP

    B->>DB: INSERT PENDING (same business transaction)
    Note over B,DB: payload is AES-GCM encrypted
    R->>DB: claim due rows with FOR UPDATE SKIP LOCKED
    R->>DB: PENDING -> PROCESSING (short transaction)
    R->>K: publish email.notification.V2
    K-->>R: broker acknowledgement
    R->>DB: PROCESSING -> PUBLISHED
    K-->>C: deliver event
    C->>C: skip if eventId in processed_events
    C->>C: decrypt payload
    C->>S: send email
    S-->>C: accepted
    C->>DB: PROCESSING/PUBLISHED -> DELIVERED
    C->>DB: INSERT processed_events
    C->>K: acknowledge offset
```

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> PROCESSING: relay claim
    PROCESSING --> PUBLISHED: Kafka acknowledged
    PROCESSING --> PENDING: publish failure with backoff
    PROCESSING --> FAILED: publication attempts exhausted
    PROCESSING --> FAILED: poison payload
    PUBLISHED --> DELIVERED: SMTP accepted
    PUBLISHED --> FAILED: delivery retries exhausted
    DELIVERED --> [*]
    FAILED --> [*]
```

Guarantees and recovery:

* **At-least-once delivery.** The consumer records success after SMTP accepts
  the message. A crash in the narrow window between SMTP and the marker can
  produce a duplicate, never a silent loss.
* **Idempotency by `eventId`.** Duplicate Kafka deliveries are skipped.
* **Stale-claim recovery.** A row stuck in `PROCESSING` beyond
  `email.outbox.relay.stale-claim-timeout` is returned to `PENDING`.
* **Backoff.** Publication retries use exponential backoff capped at
  `email.outbox.relay.max-backoff`.
* **DLQ.** Delivery failures exhaust a bounded retry budget and are published
  to `email.notification.dlq.V2`; the outbox row is marked `FAILED`.
* **Retention.** A scheduled cleanup removes terminal `PUBLISHED`/`DELIVERED`
  rows after `email.outbox.retention`.

The full operational details, state machine and replay SQL are in
[docs/email-notification-outbox.md](email-notification-outbox.md).

---

## 11. Donor search and notification

### 11.1 Active search path

The wired path is `POST /api/search/eligible-donors →
DonorSearchServiceImpl`. It uses PostgreSQL for candidate selection and
in-memory Haversine distance calculation.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant A as DonorSearchController
    participant S as DonorSearchServiceImpl
    participant DB as PostgreSQL
    participant G as GeoLocationService
    participant R as Redis

    C->>A: POST /api/search/eligible-donors
    A->>A: apply default radius (30 km)
    A->>S: findNearByEligibleDonors(request)
    S->>DB: SELECT users by blood group + exact city
    S->>G: geocode requester city if coordinates missing
    G->>R: cached city lookup
    G-->>S: latitude / longitude
    S->>S: partition donors with and without coordinates
    S->>G: geocode up to 10 donors in parallel (5 s timeout)
    S->>DB: persist newly geocoded donors
    S->>S: Haversine distance <= radius
    S->>S: filter by last donation date
    S-->>A: eligible donors
    A-->>C: donor list + count + elapsed time
```

Current search details:

* The candidate query is **blood group + exact city**, not a geo radius query.
  Distance is applied afterwards in memory.
* The requester's city is geocoded through Nominatim and cached in Redis
  (`geolocations` cache, 30-day TTL).
* Up to ten donors without coordinates are geocoded per request, in parallel on
  the `geoFetchExecutor`, with a five-second deadline. Results are persisted.
* Radius defaults to `default.geo.search.radius` (30 km) when the request omits
  it or supplies a non-positive value; validation caps it at 500 km.
* `DonorUtils.filterEligibleDonors` currently compares `lastDonationDate`
  against a cutoff. See [known gaps](#15-known-gaps-and-current-state): the
  configured `min.days.between.donation` value is not injected into the static
  field, and the filter does not check availability or verification.

### 11.2 Administrative donor notification

`POST /api/search/notify-near-by-donors` is restricted to admins. It finds
nearby donors, filters eligibility, and calls `NotificationManager`, which
enqueues one `email_outbox` row per donor. SMTP is then handled asynchronously
by the outbox pipeline.

```mermaid
flowchart LR
    admin[Admin client] -->|"POST /api/search/notify-near-by-donors"| controller[DonorSearchController]
    controller --> service[DonorSearchServiceImpl]
    service --> db[(PostgreSQL)]
    controller --> notify[NotificationManager]
    notify --> pii[PiiService.decrypt email]
    notify --> outbox[(email_outbox)]
    outbox --> relay[EmailOutboxRelayWorker]
    relay --> kafka[(email.notification.V2)]
    kafka --> consumer[EmailNotificationConsumer]
    consumer --> smtp[SMTP]
```

### 11.3 Reserved / not-yet-wired search stack

The repository contains a more advanced Redis GEO and Elasticsearch search
design that is **not connected to a controller**:

| Component | State |
| --- | --- |
| `RedisGeoSearchService` | Reads `geo:donors:<bloodGroup>` with `GEOSEARCH`, applies a 30 req/min Lua rate limit and emits Micrometer metrics. No active caller. |
| `RadiusExpander` | Expands common blood groups through 10/25/50 km and rare groups through 25/50/70 km. No active caller. |
| `ElasticSearchService` (geo-distance query) | Entire class is commented out. |
| `search/dto` donor events and analytics DTOs | Present but unused. |
| `DonorListPublisher` / `DonorListConsumer` | Legacy donor-list Kafka path. The publisher is not injected anywhere, and the configured topic names do not match (`blood-donor-notification` vs `blood-donor-notifications`). |

Treat these as **planned capabilities**, not part of the current runtime
contract, until an endpoint and index bootstrap are wired.

### 11.4 Request accept and realtime chat

`POST /api/blood-request/{id}/accept` is **first-donor-wins**. A conditional
`UPDATE ... WHERE status = 'PENDING'` claims the request and records the
accepted donor; the loser gets `REQUEST_ALREADY_ACCEPTED`. The winner gets a
single private `chat` row (idempotent: an existing chat for the request is
reused), and the requester gets an in-app notification plus a realtime badge
push. No email is sent at accept time.

Messages are **REST-first**: `POST /api/chat/{chatId}/messages` persists the
message, enforces participant-only access, applies a per-user Redis send rate
limit, then publishes `chat.message.sent.V2`. The consumer turns the message
into a recipient notification and the origin node pushes it over STOMP.

```mermaid
sequenceDiagram
    autonumber
    participant D as Donor
    participant A as API node A
    participant DB as PostgreSQL
    participant K as Kafka
    participant R as Redis pub/sub
    participant B as API node B
    participant C as Requester

    D->>A: POST /api/blood-request/{id}/accept
    A->>DB: UPDATE ... WHERE status='PENDING' (atomic)
    A->>DB: INSERT chat (or reuse existing)
    A->>DB: INSERT notification (requester)
    A-->>D: 200 + chat id + donor name
    Note over A: after commit: local STOMP push +<br/>redis fanout envelope

    D->>A: POST /api/chat/{chatId}/messages
    A->>DB: INSERT chat_message
    A->>K: publish chat.message.sent.V2 after commit
    A->>R: publish FanoutEnvelope
    R-->>B: envelope
    B->>C: STOMP /user/queue/chat
```

Delivery is **at-least-once** and never depends on the sender and receiver
sharing a node. `ChatPushService` publishes a `FanoutEnvelope` (recipient id,
destination, pre-serialized payload) to `sondhan:ws:fanout`; every node
subscribes and attempts local delivery, which is a no-op where the user is not
connected. Presence is tracked in Redis (`ws:presence:<userId>`) with a short
TTL. Clients recover missed messages by keyset backfill over REST (`after`
cursor), so a dropped WS frame is not data loss. Delivery is triggered from
`afterCommit`, so a recipient can never receive a message that later rolls back.

### 11.5 Notification center and daily digest

Every product event (chat message, request accepted, urgent alert, donation
state change) writes a `notification` row and pushes the new badge/feed entry
over `/user/queue/notifications`. The REST feed and unread count are keyset
paginated. Notifications are the **single source of truth** for in-app activity.

Email is deliberately decoupled from individual events. `NotificationDigestWorker`
runs daily, selects users with unread, undigested notifications older than
`app.notification.digest.min-age-hours`, sends one summary email through the
transactional outbox (`EmailNotificationType.DIGEST`), and stamps
`email_digest_at`. Keyset pagination over `recipient_id` plus a per-user
cooldown (`cooldown-hours`) makes the sweep resumable and prevents duplicate
digests across retries or multiple instances (ShedLock). The worker is the only
producer of product-event email.

Mobile push (FCM) is optional. `FcmPushSender` resolves the recipient's device
tokens and dispatches asynchronously on a **bounded** thread pool with a
`DiscardOldestPolicy`, so a push burst can never block a business thread or
grow memory without limit. Invalid/unregistered tokens reported by FCM are
deleted.

### 11.6 Document service

Profile photos and medical reports are stored in **MinIO** (S3-compatible),
never on the application node. The API records metadata (`bucket`, `object_key`,
`checksum_sha256`, `verification_status`) in `document` and issues short-lived
presigned URLs on demand. Uploads are size/type validated and hashed; deletes
remove both the row and the object. Buckets are created idempotently at startup
(`StorageBucketInitializer`, best-effort) and by the one-shot `minio-init`
Compose service in deployment. Download filenames are sanitized before being
placed into the presigned `response-content-disposition` header. A MinIO outage
fails only document operations; the rest of the API remains available.

### 11.7 Donation completion

A donation moves through an explicit, conditional state machine so concurrent
callers cannot double-count:

```mermaid
stateDiagram-v2
    [*] --> ACCEPTED: donor accepts request
    ACCEPTED --> PENDING_APPROVAL: donor marks complete
    PENDING_APPROVAL --> COMPLETED: requester approves (+ optional 1–5 rating)
    PENDING_APPROVAL --> DISPUTED: requester disputes with reason
    PENDING_APPROVAL --> COMPLETED: 3-day auto-confirm (no rating)
    DISPUTED --> COMPLETED: admin resolves as approved
    DISPUTED --> CANCELLED: admin resolves as rejected
    COMPLETED --> [*]
    CANCELLED --> [*]
```

Approval applies a race-safe running-average rating (`rating` and `ratingCount`
updated in one JPQL `SET` evaluated against the pre-update row) and increments
`totalDonations` / `lastDonationDate`. Each `donation_record` is unique per
request. `DonationCompletionWorker` auto-confirms stale `PENDING_APPROVAL`
records after `app.donation.auto-approve-days` (default 3) without applying a
rating, so a donor is never held hostage by an unresponsive requester. Every
transition is a conditional update, making workers and retries idempotent.

### 11.8 Urgent escalation and location freshness

Urgent requests escalate to ever-wider donor rings until they are accepted or
expire. `RequestEscalationWorker` (ShedLock) reads the configured ladder
(`app.escalation.*`), calls the nearby search at each radius, excludes donors
already alerted (`alertedUserIds`), sends notifications, and advances the
request's `escalationLevel`. Requests also carry an urgency-driven `expiresAt`
TTL; `expireStaleRequests` closes them atomically.

Freshness is part of the contract:

* Clients publish location via `POST /api/user/location`; `LocationIngestServiceImpl`
  throttles per user, writes `geoLocation` + `geoUpdatedAt`, and publishes
  `user.location.updated.V2` after commit. The handler re-applies the row
  (idempotent replay) and updates the Elasticsearch geo fields.
* Nearby and escalation search ignore donors whose `geoUpdatedAt` is older than
  `app.location.staleness-threshold-days`, so an old city centroid is never
  presented as a live position.
* `LocationStalenessWorker` nudges donors with stale locations
  (`geoReconfirmRequestedAt`, `reconfirm-cooldown-days`) and marks them for
  reconfirmation rather than silently trusting stale coordinates.

---

## 12. Audit logging

Security-relevant actions are recorded asynchronously:

| Event | Trigger |
| --- | --- |
| `REGISTER` | New account created |
| `LOGIN` | Successful or failed password login |
| `LOGOUT` | Tokens revoked |
| `OTP_SENT` / `OTP_FAILED` / `OTP_VERIFIED` / `OTP_REUSE_ATTEMPT` | OTP lifecycle and replay attempts |
| `REFRESH_TOKEN_ROTATED` | Refresh rotation |
| `REFRESH_REUSE_ATTACK` | Consumed refresh token reused |
| `ACCOUNT_LOCKED` | Locked account login attempt |
| `ROLE_CHANGED`, `TOKEN_REVOKED`, `ACCOUNT_DEACTIVATED` | Administrative/security events |

Implementation notes:

* `AuditService` is `@Async` and uses `REQUIRES_NEW`, so audit writes survive a
  rolled-back business transaction.
* `audit_logs` is partitioned by `created_at`; migration `V25` provides a
  partition-management function and a default partition exists.
* No automatic audit-retention job is currently scheduled. Partition
  maintenance/retention is an operational responsibility.

---

## 13. Reliability and failure handling

| Concern | Implementation |
| --- | --- |
| Database / Kafka consistency | Domain events publish after commit; email uses the transactional outbox |
| Duplicate delivery | `processed_events` by `eventId`; email consumer checks before sending |
| Producer failure | Retry topic, then DLQ; the blocking producer throws for the outbox relay |
| Consumer failure | Fixed backoff, then the generic factory logs and skips; only the email consumer has a DLQ recoverer and non-retryable payload errors |
| Worker duplication | ShedLock (`shedlock` table) limits scheduled jobs to one instance |
| Stale outbox claims | `locked_at` + stale-claim sweep |
| Concurrent outbox relays | `FOR UPDATE SKIP LOCKED` |
| Geo claim races | Conditional `UPDATE ... WHERE geo_status = 'PENDING'` |
| Geocoder outages | Bounded retries, `FAILED` after 5 attempts, Redis cache for successful cities |
| Concurrent accept / donation transitions | Conditional `UPDATE ... WHERE status = <expected>`; loser gets a domain error, workers are idempotent |
| WebSocket delivery | Redis pub/sub fanout after commit; at-least-once, with REST keyset backfill for missed frames |
| Push bursts | Bounded executor + `DiscardOldestPolicy`; invalid tokens pruned |
| Digest duplication | Keyset pagination + per-user cooldown + `email_digest_at` stamp + ShedLock |
| Worker repository writes | `@Modifying` repository methods are `@Transactional`, so they run atomically when invoked from a thread with no ambient transaction |
| MinIO outage | Document endpoints fail with a normalized storage error; API startup and other features stay available |
| Graceful shutdown | `server.shutdown=graceful`, 30 s lifecycle timeout, 45 s container stop grace |
| Elasticsearch outage | Indexing fails, the event is retried three times, then skipped; PostgreSQL remains authoritative and the index must be rebuilt manually |

Important caveat: `GenericKafkaEventProducer.publishEvent` is fire-and-forget.
For events that must not be lost, use the outbox or the blocking publisher.
`UserServiceImpl` already publishes after commit and logs failures without
failing the client response, so a Kafka outage can leave the read model behind
until the event is replayed or the index is rebuilt manually.

---

## 14. Configuration map

| Area | Properties / environment |
| --- | --- |
| HTTP | `server.port=8088`, `server.servlet.context-path=/sondhan` |
| PostgreSQL | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Flyway | `spring.flyway.enabled=true`, `locations=classpath:/db/migration` |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS`, `kafka.topic.version=V2`, producer acks/retries/compression |
| Email outbox | `email.outbox.relay.*`, `email.outbox.retention`, `sondhan.email.payload-key` |
| Redis | `REDIS_CLUSTER_NODES`, `REDIS_PASSWORD`, Lettuce cluster topology refresh |
| Elasticsearch | `ELASTICSEARCH_HOST`, `ELASTICSEARCH_PORT`, `ELASTICSEARCH_SCHEME`, index names |
| Geocoding | `geo.fetch.lat.long.url`, `geo.enrichment.poll-interval`, `default.geo.search.radius` |
| JWT | `JWT_ACCESS_TOKEN_VALIDITY`, `JWT_REFRESH_TOKEN_VALIDITY`, `JWT_ISSUER`, `RSA_PRIVATE_KEY_PATH`, `RSA_PUBLIC_KEY_PATH` |
| PII / OTP | `PII_ENCRYPTION_KEY`, `PII_HASH_KEY`, `OTP_HASH_KEY` |
| Auth limits | `AUTH_FAILURE_MAX_ATTEMPTS`, `AUTH_FAILURE_WINDOW_MINUTES`, `app.rate-limit.*` |
| Storage (MinIO) | `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_REGION`, `app.storage.ensure-buckets`, `app.storage.presign-expiry-seconds`, `app.storage.max-upload-bytes` |
| Push (FCM) | `app.push.enabled`, `GOOGLE_APPLICATION_CREDENTIALS` / `app.push.firebase-credentials`, `app.push.*` |
| Chat / WS | `app.chat.send.rate-limit-per-minute`, `app.chat.presence.ttl-seconds`, `app.ws.*` allowed origins |
| Location | `app.location.update.min-interval-seconds`, `app.location.staleness-threshold-days`, `geo.staleness.*`, `app.location-reconfirm.url` |
| Escalation | `app.escalation.*` ladder and cadence, urgency-driven request TTLs |
| Donations | `app.donation.auto-approve-days`, `app.donation.auto-approve.cron`, `app.donation.auto-approve.max-per-run` |
| Digest | `app.notification.digest.cron`, `min-age-hours`, `cooldown-hours`, `batch-size`, `max-per-run`, `items-per-email` |
| CORS | `sondhan.cors.allowed-origins` / `CORS_ORIGIN_1..3` |
| Mail | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` |
| Actuator | `health,info` exposed; liveness/readiness probe groups enabled |
| API docs | `SPRINGDOC_ENABLED` |

Development configuration imports an optional `.env` file. Production overrides
this with Docker secrets through
`SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`.

---

## 15. Known gaps and current state

These are deliberate notes so the architecture does not overstate what is
implemented:

1. **`user.updated` has no handler.** `UserEventConsumer` subscribes to it, but
   only the registered, geo-enriched, location-updated and chat-message
   handlers are registered. An update event fails dispatch, is retried three
   times by the generic listener error handler, then is logged and skipped.
   There is no handler-level DLQ for this consumer.
2. **Redis GEO search is not wired.** `RedisGeoSearchService` and
   `RadiusExpander` have no callers. The active nearby search is the
   Elasticsearch geo-distance path in
   `UserElasticSearchCustomRepositoryImpl`; the eligible-donor path is still
   SQL + Haversine in `DonorSearchServiceImpl`.
3. **Donor-list Kafka path is legacy.** `DonorListPublisher` is unused and its
   topic (`blood-donor-notification`) differs from the consumer topic
   (`blood-donor-notifications`). The active notification path goes directly
   through `NotificationManager` to the outbox.
4. **Eligibility filter is incomplete.** `DonorUtils.minGapDays` is a static
   field annotated with `@Value`; Spring does not inject static fields, so the
   cutoff behaves as zero days. The filter also ignores `isAvailable` and
   `isVerified`.
5. **Blood-request geo enrichment is not implemented.** `blood_request` now has
   the same freshness columns as `app_user` (V42), but no worker claims or
   enriches request rows; escalation relies on the requester-supplied
   coordinates.
6. **Two Elasticsearch models share `registered_donors`.** The read model is
   `RegisteredDonorDocument`; the admin API uses `DonorSearchDocument`. The
   admin paginated repository points at `donor_search_index` while the
   document annotation points at `registered_donors`; verify index alignment
   before relying on admin search.
7. **JWT keys are ephemeral without explicit paths.** In development, a
   temporary RSA key pair is generated, so tokens do not survive a restart and
   are not shared across instances. Keys are loaded filesystem-first when
   `RSA_PRIVATE_KEY_PATH`/`RSA_PUBLIC_KEY_PATH` are set. Production must mount
   a stable key pair. There is no `kid`-based key rotation yet.
8. **No audit retention job.** Audit partitions and retention are operational
   tasks; the default partition exists but old partitions are not dropped
   automatically.
9. **Spring Session is present but unused for authentication.** The
   dependency is declared, but no session store is configured; the security
   filter chain is `STATELESS` and no Spring Session filter is enabled, so
   sessions are not part of the auth path.
10. **Flyway validation is disabled** (`validate-on-migrate=false`) pending
    environment baselining. Enable it when all environments have a clean
    history.
11. **No request-ID propagation.** Logback includes a `requestId` MDC field,
    but no servlet filter or client-context builder populates it, so log lines
    cannot currently be correlated to a request.
12. **No stale-claim recovery for geo enrichment.** A row left in `PROCESSING`
    by a crash or a failed event publish is not swept back to `PENDING`; only
    `app_user` rows in `PENDING` are picked up by the worker.
13. **No automated Elasticsearch reindex job.** The read model can be rebuilt
    from PostgreSQL, but recovery currently requires a manual replay or
    bulk-index operation.
14. **Single-node WebSocket fanout.** Redis pub/sub gives cross-node delivery,
    but there is no durable queue for offline clients; users recover through
    REST backfill when they reconnect.

---

## 16. Keeping this document current

Every development change that affects architecture must update this file in the
same commit/PR. Use this checklist:

- [ ] Did a new component, package, external dependency or data store appear?
      Update the context/container diagrams and the package table.
- [ ] Did an API endpoint, auth rule, or role change? Update the API and
      authentication sections.
- [ ] Did a table, index, Redis key family or event/topic change? Update the
      data model and event tables.
- [ ] Did a background job, retry policy or failure mode change? Update the
      reliability section and the relevant sequence/state diagram.
- [ ] Did deployment topology or configuration change? Update the configuration
      table and [docs/container-deployment.md](container-deployment.md) if
      operating procedures changed.
- [ ] Add a row to the change log below.

Recommended style:

* Keep diagrams at the **component / flow** level. Do not turn this into a class
  diagram.
* Prefer a sequence diagram for a multi-step flow and a state diagram for a
  lifecycle.
* Mark planned or dormant code explicitly as **reserved** or **not wired**, as
  done in [section 11.3](#113-reserved--not-yet-wired-search-stack).
* Link to focused documents (outbox, deployment) instead of duplicating their
  operational detail.

---

## 17. Change log

| Date | Change | Where |
| --- | --- | --- |
| 2026-11-15 | Initial architecture document: context, containers, domain model, APIs, auth, PII, event flows, outbox, search, geo enrichment, audit, reliability, gaps | `docs/architecture.md` |
| 2026-11-15 | Documented the geo-enrichment handler transaction boundary; the `@Modifying` geo update now runs inside `GeoEnrichedEventHandler.handle(...)`, with the idempotency marker in a separate `REQUIRES_NEW` transaction | §9.2, `GeoEnrichedEventHandler` |
| 2026-11-15 | Adopted Compose project `sondhan` and migrated existing dev data into `sondhan_*` volumes; pinned PostgreSQL to the Debian `postgres:15` image for collation compatibility; fixed the Redis service anchor and Elasticsearch log-index replicas | `docs/container-deployment.md` |
| 2026-11-15 | Added RBAC (`ROLE_DONOR`/`ROLE_ORG_ADMIN`/`ROLE_SYSTEM_ADMIN`), per-IP rate limiting, OTP salting, anti-enumeration and principal-bound writes | §5, §6 |
| 2026-11-15 | Added accept-first-donor-wins + private chat with REST persistence and Redis-fanout STOMP delivery | §5, §11.4 |
| 2026-11-15 | Added notification center, realtime badge push, optional FCM push and the daily digest email worker | §5, §11.5 |
| 2026-11-15 | Added MinIO document service with presigned URLs; added MinIO/`minio-init` to Compose | §11.6, `docs/container-deployment.md` |
| 2026-11-15 | Added donation completion state machine with rating and 3-day auto-confirm | §11.7 |
| 2026-11-15 | Added `search_after` pagination, expanding-radius nearby search, urgent escalation ladder and location freshness/staleness pipeline | §11.1, §11.8 |
| 2026-11-15 | Added `user.location.updated` and `chat.message.sent` topics; documented new reliability and configuration entries | §8, §13, §14 |
