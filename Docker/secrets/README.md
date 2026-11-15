# Docker secrets for the production Compose stack

Everything in this directory except this README and `.gitignore` is ignored by
Git. Never commit real keys or passwords.

`Docker/docker-compose.prod.yml` mounts these files into the application at
`/run/secrets/` and the app imports that directory with
`SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`. The file name becomes
the property name, so `db_password` would be imported as `db_password`; the
Compose entries therefore use explicit targets such as `DB_PASSWORD`, which
resolve `${DB_PASSWORD}` in `application.properties`.

## Required files

| File | Property | Notes |
|---|---|---|
| `db_password` | `DB_PASSWORD` | PostgreSQL password |
| `mail_password` | `MAIL_PASSWORD` | SMTP password / app password |
| `pii_encryption_key` | `PII_ENCRYPTION_KEY` | AES key for PII encryption |
| `pii_hash_key` | `PII_HASH_KEY` | keyed hash for PII lookup |
| `otp_hash_key` | `OTP_HASH_KEY` | keyed hash for OTPs |
| `email_payload_key` | `EMAIL_PAYLOAD_KEY` | key for the email outbox payload |
| `minio_access_key` | `MINIO_ACCESS_KEY` | object-storage service account (not root) |
| `minio_secret_key` | `MINIO_SECRET_KEY` | object-storage service account secret |
| `rsa_private.pem` | file path | PKCS#8 JWT signing key |
| `rsa_public.pem` | file path | X.509 public key |

## Create the files

```bash
cd Docker
mkdir -p secrets
chmod 700 secrets

# Random secrets (generate once, store in your secret manager/backup)
for f in pii_encryption_key pii_hash_key otp_hash_key email_payload_key; do
  openssl rand -base64 48 > "secrets/$f"
done

# Secrets you must obtain from the real systems
printf '%s' 'postgres-password' > secrets/db_password
printf '%s' 'smtp-app-password' > secrets/mail_password
printf '%s' 'object-storage-access-key' > secrets/minio_access_key
printf '%s' 'object-storage-secret-key' > secrets/minio_secret_key

# JWT signing key pair (outside the repository; back this up)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out secrets/rsa_private.pem
openssl pkey -in secrets/rsa_private.pem -pubout -out secrets/rsa_public.pem

chmod 600 secrets/*
chmod 644 secrets/rsa_public.pem
```

Rotating `pii_encryption_key`, `pii_hash_key`, `otp_hash_key` or
`EMAIL_PAYLOAD_KEY` invalidates existing encrypted values or hashes. Plan a
migration before rotating them. Rotating the RSA key pair invalidates issued
tokens; that is usually acceptable and forces re-login.

Passwords cannot contain a newline. Do not leave a trailing newline in the
single-line secret files; the commands above use `printf` for that reason.
