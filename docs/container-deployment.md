# Running the service with Docker and Compose

This document explains how Docker Compose is used for this project, how the
containers should be operated, and what was hardened as part of the container
setup. It is the reference for both local development and production-like
single-VM deployments.

## 1. How Compose should be used in a production-like system

Docker Compose is a **single-host container orchestrator**. It is a good fit
for:

* local development and integration testing;
* a staging environment on one VM;
* a small production deployment on one VM or a single cloud instance, where a
  short maintenance window and manual failover are acceptable.

It is **not** an HA production orchestrator. Compose:

* cannot reschedule containers when a host dies;
* has no multi-node scheduling, rolling updates, self-healing or autoscaling;
* cannot spread replicas across availability zones;
* has no built-in secret manager, service mesh, or fine-grained RBAC.

For a real production system, use one of:

| Requirement | Recommended platform |
|---|---|
| High availability, rolling deploys, autoscaling | Kubernetes / EKS / GKE / AKS |
| Container orchestration on AWS | ECS Fargate + ALB |
| Data services | Managed PostgreSQL, Kafka (MSK/Confluent), Redis (ElastiCache/MemoryDB), Elasticsearch (OpenSearch/Elastic Cloud) |
| Reverse proxy / TLS | Cloud load balancer, nginx, Caddy or Traefik |

The production Compose file in this repository therefore runs the **application
only** by default and points it at managed services. A self-hosted
infrastructure profile exists for small deployments and is explicitly a
single-node, non-HA compromise.

### Compose operating rules

1. Treat the Compose file as code and review changes like application code.
2. Pin image tags (and preferably digests). `latest` is only the fallback for
   the locally built application image.
3. Keep secrets out of Compose files, `.env` files and Git. Use Docker secrets
   (or your orchestrator's secret store).
4. Give every long-running service a `restart` policy, a health check, a
   `stop_grace_period` and resource limits.
5. Use a separate Compose project name and a separate host per environment.
6. Back up volumes and databases before every upgrade; Flyway migrations are
   forward-only.
7. Put a TLS-terminating reverse proxy in front of the application. Never
   publish the app port on `0.0.0.0` in production.
8. Monitor `/actuator/health/liveness` for process health and
   `/actuator/health/readiness` for dependency health.

## 2. How containers are used in this project

The application is a Spring Boot 3 service with these runtime dependencies:

* PostgreSQL (primary datastore, Flyway migrations);
* Kafka (domain events and the email notification outbox);
* Redis cluster (sessions, rate limits, idempotency, chat presence and WS fanout);
* Elasticsearch (donor search and application logs);
* MinIO / S3 (documents and profile photos, presigned access);
* optionally Firebase Cloud Messaging (mobile push — disabled by default).

Two Compose files describe the two supported modes.

### 2.1 Development stack — `Docker/docker-compose.yml`

```
docker compose --env-file .env -f Docker/docker-compose.yml up -d --build
```

This starts:

| Service | Purpose | Host port |
|---|---|---|
| `app` | application image built from the repo | `8088` |
| `postgres` | PostgreSQL 15 (Debian image) | `5432` |
| `kafka` + `zookeeper` | single broker | `9092` |
| `elasticsearch` | single node, security disabled | `9200`, `9300` |
| `redis-1` … `redis-6` | 3 masters + 3 replicas, hostname announcements | none |
| `redis-cluster-init` | one-shot `redis-cluster create` | none |
| `minio` | S3-compatible document storage | `127.0.0.1:9000` (API), `127.0.0.1:9001` (console) |
| `minio-init` | one-shot bucket creation, then exits | none |

Optional developer tooling is behind profiles:

```
# Kafka UI (AKHQ)
docker compose --env-file .env -f Docker/docker-compose.yml --profile kafka-ui up -d

# Kibana + Logstash
docker compose --env-file .env -f Docker/docker-compose.yml --profile observability up -d
```

To run only the infrastructure and keep the application in the IDE:

```
docker compose --env-file .env -f Docker/docker-compose.yml up -d \
  postgres kafka elasticsearch redis-cluster-init
```

Notes specific to development:

* The application container overrides `DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`,
  `ELASTICSEARCH_*` and `REDIS_CLUSTER_NODES` with Compose service names. Values
  in `.env` are host-oriented and are used when the app runs outside Docker.
* The Redis cluster nodes advertise their Compose service names
  (`redis-1:6379`, …) so cluster redirections work from the app container. The
  host-based cluster from `Docker/setup-redis-cluster.sh` advertises
  `127.0.0.1` and only works for a host-run application.
* The development stack publishes infrastructure ports for convenience. The
  production file does not.
* `.env` is loaded with `spring.config.import=optional:file:.env[.properties]`
  and must exist for Compose interpolation (`--env-file .env`).
* The Compose project is `sondhan`, so volumes are named `sondhan_*`. The
  `postgres` service uses the Debian `postgres:15` image rather than
  `postgres:15-alpine`: Alpine is musl-based and creates a different collation,
  which PostgreSQL reports as *"database ... has no actual collation version"*
  when it opens a data directory created by a glibc image. Keep the image family
  aligned with the data directory, or reindex/dump-restore after switching.
* MinIO is bound to loopback only and stores objects in the `minio-data` volume.
  The app talks to `http://minio:9000` inside the Compose network and waits for
  `minio-init` to finish. `minio-init` creates the `sondhan-profile` and
  `sondhan-medical` buckets idempotently; development defaults are
  `sondhan-admin` / `sondhan-secret` and **must be overridden** in any shared
  environment. The app also re-creates missing buckets at startup
  (`app.storage.ensure-buckets=true`) as a best-effort safety net.
* Presigned document URLs are generated against `MINIO_ENDPOINT`. A browser must
  be able to reach that hostname, so in production point it at the public
  S3/MinIO endpoint (or a proxy in front of it), not at an internal address.

### 2.2 Production stack — `Docker/docker-compose.prod.yml`

Recommended: managed infrastructure, application only.

```bash
cp Docker/prod.env.example Docker/prod.env
chmod 600 Docker/prod.env
# create Docker/secrets/* as described in Docker/secrets/README.md

docker compose --env-file Docker/prod.env \
  -f Docker/docker-compose.prod.yml up -d
```

Small deployment: also run the infrastructure on the same VM.

```bash
docker compose --env-file Docker/prod.env \
  -f Docker/docker-compose.prod.yml --profile self-hosted-infra up -d
```

Production properties:

* only `127.0.0.1:${APP_PORT}` is published, for a host reverse proxy;
* infrastructure services publish no host ports;
* the container runs as a non-root user with a read-only root filesystem, a
  `no-new-privileges` security option, and `/tmp` on tmpfs;
* logs are rotated by the Docker logging driver and the application writes to
  the `app-logs` volume;
* secrets are mounted under `/run/secrets` and imported through
  `SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/`;
* Swagger/OpenAPI is disabled with `SPRINGDOC_ENABLED=false`;
* the application uses the liveness probe for its Docker health check.

Production deployment does **not** include MinIO. Point `MINIO_ENDPOINT` at a
managed S3-compatible service (or a separate MinIO host), provide
`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` through the secret manager, and pre-create
the `sondhan-profile` and `sondhan-medical` buckets with a least-privilege
policy. `MINIO_ENDPOINT` must be publicly resolvable by clients because
presigned download URLs are generated against it; keep the write path private.

Realtime chat uses STOMP over WebSocket at `/sondhan/ws`. The reverse proxy must:

* forward the WebSocket upgrade headers (`Upgrade`, `Connection`) and not
  buffer the connection;
* allow long-lived idle connections (at least the presence TTL, 300 s) and send
  periodic pings;
* keep the same host/path as the REST API, so the client can reuse the access
  token and the CORS allow-list (`CORS_ORIGIN_*`) applies to the handshake.

Sticky sessions are **not** required: cross-node delivery uses the
`sondhan:ws:fanout` Redis pub/sub channel, and clients backfill missed messages
over REST. Redis must therefore be reachable from every application instance.

Mobile push is opt-in. Set `PUSH_ENABLED=true` and mount a Firebase service
account JSON, exposing its path as `FCM_CREDENTIALS_PATH`. When disabled, the
notification center still works through WebSocket and REST; only device push is
skipped.

On the first boot of the self-hosted profile the application may restart a few
times until PostgreSQL, Kafka and Elasticsearch are ready. `restart: always`
handles that; wait for `docker inspect --format '{{.State.Health.Status}}'
sondhan-app` to report `healthy`. Compose cannot express a conditional
dependency that only exists when a profile is enabled, so the application does
not use `depends_on` for the optional infrastructure.

### 2.3 Image build

The `Dockerfile` is a multi-stage build:

```
docker build -t registry.example.com/sondhan/blood-donor-finder:1.0.0 .
docker push registry.example.com/sondhan/blood-donor-finder:1.0.0
```

* Maven dependencies are cached in a BuildKit cache mount, so source-only
  changes do not re-download them.
* The runtime image is `eclipse-temurin:17-jre-jammy` with `tini` as PID 1.
* The application runs as the unprivileged `sondhan` user.
* `JAVA_OPTS` uses container-aware memory sizing (`MaxRAMPercentage=75`) and
  `-XX:+ExitOnOutOfMemoryError`.
* The container health check calls
  `/sondhan/actuator/health/liveness`.

## 3. Configuration and secrets

`application.properties` still supports the development `.env` import. In
production, `SPRING_CONFIG_IMPORT` is overridden to read a config tree:

```
SPRING_CONFIG_IMPORT=optional:configtree:/run/secrets/
```

Secrets are mounted with explicit targets, so the file name is the property
name and resolves `${DB_PASSWORD}`, `${PII_ENCRYPTION_KEY}`, and so on. The
files are listed and generated in `Docker/secrets/README.md`.

Rules:

* Never commit `.env`, `Docker/prod.env`, `Docker/secrets/*`, JWT keys or
  database passwords.
* Use a real secret manager when available (AWS Secrets Manager, Vault, SSM,
  Kubernetes Secrets + external-secrets). Docker secrets are files on the host;
  they are not encrypted at rest by Docker.
* Back up `pii_encryption_key`, `pii_hash_key` and `otp_hash_key`. Losing them
  makes existing encrypted data and hashes unrecoverable.
* Object storage credentials (`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`) are
  secrets. Use a dedicated service account scoped to the two document buckets,
  never the MinIO root user.
* Firebase credentials (`FCM_CREDENTIALS_PATH`) are secrets; mount the service
  account file read-only and keep it out of the image and Git.
* Rotating PII/OTP keys requires a data migration. Rotating the JWT key pair
  invalidates issued tokens.
* For managed PostgreSQL, include TLS in `DB_URL` (for example
  `?sslmode=require`) and restrict network access to the application host.

## 4. Backups and restore

| Data | Backup |
|---|---|
| PostgreSQL | `pg_dump` on a schedule, plus provider automated backups / PITR |
| Elasticsearch | Snapshot repository to object storage; search data is rebuildable but snapshots speed recovery |
| Kafka | Replication is not backup. Persist critical topics or rely on the outbox table; back up topic configuration |
| Redis | AOF/RDB for cache is optional; sessions can be lost (users re-login) |
| MinIO / object storage | Versioning + lifecycle on the document buckets; back up `minio-data` for the self-hosted dev stack |
| Secrets | Encrypted backup in the secret manager |

Example PostgreSQL backup from the self-hosted stack:

```bash
docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml \
  exec -T postgres pg_dump -U "$DB_USERNAME" -d "$DB_NAME" -Fc \
  > "backup-$(date +%F).dump"
```

Restore into a new database first, verify Flyway history and row counts, then
switch the application over. Do not restore over a live database during an
incident without a verified backup.

## 5. Upgrades and rollback

1. Build and push an immutable image tag and record its digest.
2. Back up PostgreSQL and, if relevant, Elasticsearch.
3. Update `APP_TAG` in `Docker/prod.env`.
4. Pull and recreate the application container:

   ```bash
   docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml pull app
   docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml up -d app
   ```

5. Wait for the health check to become healthy:

   ```bash
   docker inspect --format '{{.State.Health.Status}}' sondhan-app
   ```

6. Roll back by setting `APP_TAG` to the previous immutable tag and repeating
   step 4. Flyway migrations are forward-only, so a rollback that crosses a
   schema migration requires restoring the pre-upgrade database backup.

Container stop behavior is graceful: `server.shutdown=graceful`,
`spring.lifecycle.timeout-per-shutdown-phase=30s`, and
`stop_grace_period: 45s`. Kafka consumers finish their current record and the
email outbox relay releases its claim before the process exits.

## 6. Monitoring and health

| Signal | Endpoint / source |
|---|---|
| Liveness (process only) | `/sondhan/actuator/health/liveness` |
| Readiness (DB, Kafka, Redis, ES) | `/sondhan/actuator/health/readiness` |
| Docker health | `docker inspect --format '{{.State.Health.Status}}' sondhan-app` |
| Container logs | `docker compose ... logs -f app` (JSON, rotated) |
| Application file logs | `app-logs` volume, `/var/log/sondhan` |
| PostgreSQL | `pg_isready`, slow query log, disk |
| Kafka | consumer lag, under-replicated partitions |
| Elasticsearch | `_cluster/health`, pending tasks, disk watermark |
| Redis | `cluster info`, `cluster nodes`, memory |
| MinIO / S3 | bucket health, capacity, error rate on `PutObject`/presign |
| WebSocket | active STOMP sessions and fanout subscriber count (logs/metrics) |

`micrometer-registry-prometheus` is on the classpath. To scrape metrics, add
`prometheus` to `management.endpoints.web.exposure.include` and expose the
endpoint only on the private network or behind the reverse proxy with
authentication. Do not expose actuators publicly.

### Elasticsearch disk watermark incident and prevention

A production incident on the development host showed the failure mode:

1. the host disk reached 91%, above Elasticsearch's default 90% high
   watermark;
2. Elasticsearch refused to allocate shards and the cluster went red;
3. the master task queue wedged for ~15 hours on
   `update_tsdb_data_stream_end_times`;
4. every cluster-state update — including `put-mapping` for
   `registered_donors` — timed out with
   `process_cluster_event_timeout_exception`, which the application surfaced as
   an HTTP 503.

Recovery:

* free disk space (Docker build cache and dangling anonymous volumes are safe
  to reclaim; named project volumes are not);
* restart Elasticsearch to clear the stuck master task;
* set `number_of_replicas: 0` for indices on a single-node cluster;
* restart the application and verify `PUT /registered_donors/_mapping` and a
  test index/delete succeed.

Prevention:

* alert at 80% and page at 85% disk usage; keep at least 10% free at all times;
* never run a single-node Elasticsearch with replicas greater than `0`;
* monitor `_cluster/health` status, unassigned shards and
  `_cat/pending_tasks` age;
* cap log retention (`app-logs-*`, application logs, Docker logs);
* do not lower the watermark thresholds as a workaround for a full disk — at
  95% Elasticsearch enters flood stage and makes indices read-only.

## 7. Scope of the improvement

Implemented in this change:

* hardened multi-stage `Dockerfile` (BuildKit cache, non-root user, `tini`,
  container-aware JVM flags, log volume, liveness health check);
* stricter `.dockerignore` so secrets, logs, docs and IDE files never enter the
  build context;
* hardened development Compose stack with health checks, dependency ordering,
  restart policies, log rotation, resource limits, named volumes and optional
  tooling profiles;
* a self-contained six-node Redis cluster for containers, in addition to the
  existing host-based bootstrap script;
* a separate production Compose file with an app-only default and an optional
  single-node self-hosted infrastructure profile;
* production env template and a documented Docker secrets layout;
* graceful shutdown, health probe groups and public liveness/readiness
  actuator endpoints in the application;
* `LOG_DIR` made overridable so the container writes logs to the mounted
  `/var/log/sondhan` volume instead of a read-only image path;
* MinIO document storage plus a one-shot `minio-init` bucket bootstrap in the
  development stack, with managed object storage for production;
* realtime chat/notification WebSocket support with Redis fanout (no sticky
  sessions) and optional FCM device push;
* this operating document.

Deliberately out of scope:

* multi-node Kafka, Elasticsearch or Redis high availability;
* Kubernetes manifests / Helm charts;
* a managed-service provisioning stack (Terraform/CloudFormation);
* image vulnerability scanning and SBOM generation in CI;
* a reverse proxy container — production is expected to use the host's existing
  proxy or a cloud load balancer.

## 8. Quick reference

```bash
# Development
docker compose --env-file .env -f Docker/docker-compose.yml up -d --build
docker compose --env-file .env -f Docker/docker-compose.yml ps
docker compose --env-file .env -f Docker/docker-compose.yml logs -f app
docker compose --env-file .env -f Docker/docker-compose.yml down

# Production (app + managed infrastructure)
docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml up -d
docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml ps
docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml logs -f app

# Production (everything on one VM)
docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml \
  --profile self-hosted-infra up -d

# Validate a configuration before applying it
docker compose --env-file Docker/prod.env -f Docker/docker-compose.prod.yml config
```
