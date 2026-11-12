# Security

This document records the security model of the Sondhan service and the
operational requirements for deploying it safely.

## Reporting a vulnerability

Do not open a public issue for security problems. Report them privately to the
maintainers with reproduction steps and impact.

## Secrets

No credentials, API keys, PII encryption keys or signing keys belong in the
repository. Everything sensitive is injected through environment variables
(see `.env.example`).

| Variable | Purpose |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP credentials |
| `PII_ENCRYPTION_KEY` | pgcrypto key used to encrypt PII token values |
| `PII_HASH_KEY` | HMAC key for PII lookup hashes |
| `OTP_HASH_KEY` | HMAC key for OTP hashes |
| `RSA_PRIVATE_KEY_PATH`, `RSA_PUBLIC_KEY_PATH` | JWT signing key pair |
| `REDIS_PASSWORD` | Redis auth |
| `JWT_ISSUER` | JWT `iss` claim |

Rotate any credential that has ever been committed to git. Deleting it in a
later commit does not remove it from history.

### Local development

`application.properties` imports an optional, git-ignored `.env` file from the
working directory. Create it once with:

```bash
cp .env.example .env
chmod 600 .env
```

Set `DB_PASSWORD` to the password of the local PostgreSQL `bduser`. The Docker
stack uses the same `DB_USERNAME` and `DB_PASSWORD` values, so start it with
`docker compose --env-file ../.env up -d` from the `Docker` directory. There is
deliberately no default value for `DB_PASSWORD`: if it is missing the
application fails to start rather than falling back to a weak development
credential. Set the
remaining secrets (`PII_*`, `OTP_HASH_KEY`, mail credentials) to strong values;
for an existing local database keep the previous `PII_ENCRYPTION_KEY` so that
stored PII can still be decrypted.

### JWT signing keys

Production **must** mount a stable RSA key pair (>= 2048 bits) and point
`RSA_PRIVATE_KEY_PATH` / `RSA_PUBLIC_KEY_PATH` at it. If both are unset the
service generates an ephemeral key pair and logs a warning; tokens issued with
it are invalid after a restart and cannot be verified by other instances.

Generate a pair with:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private_key.der -outform DER
openssl rsa -in private_key.der -inform DER -pubout -out public_key.der -outform DER
```

Store the private key in a secrets manager or a file readable only by the
service account, never in the repository.

## Authentication

- Passwords are hashed with BCrypt (cost 12).
- Access and refresh tokens are signed with RS256 and whitelisted in Redis.
- Refresh tokens are single-use: consumption is atomic (`GETDEL`), and reuse
  revokes every token for the user and emits an audit event.
- Unsuccessful authentication attempts are rate limited in Redis. Counters are
  keyed by the SHA-256 hash of the operation scope, client IP and subject, so
  raw identifiers never appear in the Redis keyspace.
- `POST /api/auth/login` allows at most 10 unsuccessful responses per client IP
  and resolved user id in a 5-minute window. Requests for unknown identifiers
  share a per-IP `unknown` bucket because no user id can be resolved.
- `POST /api/auth/verify-otp` allows at most 10 unsuccessful responses per
  client IP, user id and OTP purpose in a 5-minute window, so exhausting one
  purpose does not lock the user out of the others.
- Every non-2xx response counts, including validation errors, unknown users,
  bad credentials, invalid/expired OTPs and server errors. Blocked requests
  receive `429` with a `Retry-After` header. Successful requests do not reset
  the failure budget, and blocked requests are not counted again so a client
  cannot extend its own window indefinitely.
- OTP codes are HMAC-SHA256 hashed and bound to the user id and purpose; the
  account is locked after repeated failures. Failed attempts are recorded in a
  separate transaction so a rejected verification still counts toward lockout.
- `POST /api/auth/verify-otp` returns a specific error code instead of a
  generic server error: `INVALID_OTP` (400), `OTP_EXPIRED` (400),
  `OTP_ALREADY_USED` (409), `ALREADY_VERIFIED` (409), `ACCOUNT_LOCKED` (403),
  `UNSUPPORTED_OTP_PURPOSE` (400) and `RATE_LIMITED` (429).
- OTP delivery is channel-based. Email is the default `OtpChannel`; an SMS
  channel can be added without changing OTP generation, hashing or verification.
- JWTs are validated for signature, expiry and issuer. Only `ACCESS_TOKEN`
  tokens are accepted by the authentication filter.

## Authorization

- `/api/auth/**`, `/actuator/health`, OpenAPI docs and `/error` are public.
- Everything else requires authentication.
- `/actuator/**`, `/api/mail/**`, `/es/**` and bulk donor notification are
  restricted to `SUPER_ADMIN` / `ORG_ADMIN`.
- Profile updates and blood requests derive the user from the JWT principal.
  Client-supplied user ids are never trusted.

## PII

- Email and phone numbers are stored as encrypted PII tokens. Lookup hashes are
  keyed with HMAC-SHA256; legacy SHA-256 hashes are still accepted for
  pre-existing rows.
- PII tokens, password hashes and contact details are excluded from JSON
  responses.

## Deployment checklist

- [ ] All environment variables from `.env.example` are set.
- [ ] `DB_PASSWORD`, `MAIL_PASSWORD`, `PII_*`, `OTP_HASH_KEY` are strong and unique.
- [ ] RSA key pair is mounted and `RSA_*_KEY_PATH` points to it.
- [ ] `SPRINGDOC_ENABLED=false` in production.
- [ ] TLS terminates at a trusted proxy; only trusted proxies may set
      `X-Forwarded-*` headers.
- [ ] `CORS_ORIGIN_*` lists only real front-end origins (wildcards are rejected).
- [ ] Logs are shipped to a restricted, access-controlled store.
