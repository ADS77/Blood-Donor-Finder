# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Resolve dependencies in a separate layer so source edits do not invalidate
# the Maven cache.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -Dmaven.test.skip=true package

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

ARG APP_UID=10001
ARG APP_GID=10001

# curl is required by the container health check; tini reaps child processes
# and forwards signals so graceful shutdown works.
RUN groupadd --system --gid "${APP_GID}" sondhan \
 && useradd --system --uid "${APP_UID}" --gid "${APP_GID}" \
      --home-dir /app --shell /usr/sbin/nologin sondhan \
 && apt-get update \
 && apt-get install -y --no-install-recommends curl tini \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder --chown=sondhan:sondhan /build/target/*.jar /app/app.jar

RUN mkdir -p /var/log/sondhan \
 && chown -R sondhan:sondhan /app /var/log/sondhan

ENV TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -DLOG_DIR=/var/log/sondhan"

USER sondhan

EXPOSE 8088

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:8088/sondhan/actuator/health/liveness" || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
