#!/usr/bin/env bash
set -Eeuo pipefail

# ============================================================
# Sondhan - Local 6-node Redis Cluster bootstrap
#
# Topology:
#   Master A  :6379  bus :16379
#   Replica A :6380  bus :16380
#   Master B  :6381  bus :16381
#   Replica B :6382  bus :16382
#   Master C  :6383  bus :16383
#   Replica C :6384  bus :16384
#
# Docker Engine on Linux; containers use host networking.
# ============================================================

IMAGE="${REDIS_IMAGE:-redis:7.4-alpine}"
BASE_DIR="${REDIS_CLUSTER_DIR:-/data/redis-cluster}"
RESET="${RESET_REDIS_CLUSTER:-false}"

PORTS=(6379 6380 6381 6382 6383 6384)
CONTAINERS=(
  "a-master-temp-redis"
  "a-slave-temp-redis"
  "b-master-temp-redis"
  "b-slave-temp-redis"
  "c-master-temp-redis"
  "c-slave-temp-redis"
)

log() {
  echo
  echo "============================================================"
  echo "$*"
  echo "============================================================"
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker CLI is not installed or not in PATH."
docker info >/dev/null 2>&1 || fail "Docker Engine is not running or current user cannot access it."

if [[ "$RESET" == "true" ]]; then
  log "RESET requested: removing Redis cluster containers and data"

  for container in "${CONTAINERS[@]}"; do
    docker rm -f "$container" >/dev/null 2>&1 || true
  done

  rm -rf "$BASE_DIR"
fi

mkdir -p "$BASE_DIR"

# ------------------------------------------------------------
# Create one Redis config per node.
# ------------------------------------------------------------
log "Creating Redis node configurations"

for port in "${PORTS[@]}"; do
  bus_port=$((port + 10000))
  node_dir="$BASE_DIR/$port"

  mkdir -p "$node_dir"

  cat > "$node_dir/redis.conf" <<EOF_CONF
bind 127.0.0.1
protected-mode yes
port $port

dir /data

tcp-backlog 511
timeout 0
tcp-keepalive 300

cluster-enabled yes
cluster-config-file /data/nodes.conf
cluster-node-timeout 5000
cluster-announce-ip 127.0.0.1
cluster-announce-port $port
cluster-announce-bus-port $bus_port

appendonly yes
appendfsync everysec
EOF_CONF
done

# ------------------------------------------------------------
# Start six Redis containers if they are not already running.
# ------------------------------------------------------------
log "Starting Redis containers"

for i in "${!PORTS[@]}"; do
  port="${PORTS[$i]}"
  container="${CONTAINERS[$i]}"
  node_dir="$BASE_DIR/$port"

  if docker inspect "$container" >/dev/null 2>&1; then
    state="$(docker inspect -f '{{.State.Status}}' "$container")"

    if [[ "$state" != "running" ]]; then
      echo "Starting existing container: $container"
      docker start "$container" >/dev/null
    else
      echo "Already running: $container"
    fi
  else
    echo "Creating: $container"
    docker run -d \
      --name "$container" \
      --network host \
      --restart unless-stopped \
      -v "$node_dir:/data" \
      "$IMAGE" \
      redis-server /data/redis.conf >/dev/null
  fi
done

# ------------------------------------------------------------
# Wait for every node to become reachable.
# ------------------------------------------------------------
log "Waiting for Redis nodes"

for port in "${PORTS[@]}"; do
  ready=false

  for attempt in {1..30}; do
    if docker exec "${CONTAINERS[0]}" redis-cli -h 127.0.0.1 -p "$port" ping 2>/dev/null | grep -q '^PONG$'; then
      ready=true
      break
    fi
    sleep 1
  done

  [[ "$ready" == true ]] || fail "Redis node on port $port did not become ready."
  echo "Redis $port: PONG"
done

# ------------------------------------------------------------
# Determine whether the cluster already exists.
# ------------------------------------------------------------
cluster_state="$(docker exec "${CONTAINERS[0]}" redis-cli -p 6379 cluster info 2>/dev/null | awk -F: '$1=="cluster_state" {print $2}')"
known_nodes="$(docker exec "${CONTAINERS[0]}" redis-cli -p 6379 cluster info 2>/dev/null | awk -F: '$1=="cluster_known_nodes" {print $2}')"

if [[ "$cluster_state" == "ok" && "$known_nodes" == "6" ]]; then
  log "Redis Cluster is already initialized"
else
  log "Creating 3-master / 3-replica Redis Cluster"

  docker exec "${CONTAINERS[0]}" redis-cli --cluster create \
    127.0.0.1:6379 \
    127.0.0.1:6380 \
    127.0.0.1:6381 \
    127.0.0.1:6382 \
    127.0.0.1:6383 \
    127.0.0.1:6384 \
    --cluster-replicas 1 \
    --cluster-yes
fi

# ------------------------------------------------------------
# Final validation.
# ------------------------------------------------------------
log "Validating Redis Cluster"

cluster_info="$(docker exec "${CONTAINERS[0]}" redis-cli -p 6379 cluster info)"
echo "$cluster_info"

echo

docker exec "${CONTAINERS[0]}" redis-cli -p 6379 cluster nodes

cluster_state="$(echo "$cluster_info" | awk -F: '$1=="cluster_state" {print $2}')"
slots="$(echo "$cluster_info" | awk -F: '$1=="cluster_slots_assigned" {print $2}')"
known_nodes="$(echo "$cluster_info" | awk -F: '$1=="cluster_known_nodes" {print $2}')"
cluster_size="$(echo "$cluster_info" | awk -F: '$1=="cluster_size" {print $2}')"

[[ "$cluster_state" == "ok" ]] || fail "Cluster state is '$cluster_state', expected 'ok'."
[[ "$slots" == "16384" ]] || fail "Only $slots hash slots are assigned, expected 16384."
[[ "$known_nodes" == "6" ]] || fail "Cluster knows $known_nodes nodes, expected 6."
[[ "$cluster_size" == "3" ]] || fail "Cluster has size $cluster_size, expected 3 masters."

log "Redis Cluster is READY"
cat <<EOF_MSG

Seed nodes for Spring Boot:
  127.0.0.1:6379
  127.0.0.1:6381
  127.0.0.1:6383

Example application.properties:
  spring.data.redis.cluster.nodes=127.0.0.1:6379,127.0.0.1:6381,127.0.0.1:6383

Containers:
  ${CONTAINERS[*]}

To inspect the cluster:
  docker exec -it a-master-temp-redis redis-cli -p 6379 cluster info
  docker exec -it a-master-temp-redis redis-cli -p 6379 cluster nodes

To test through cluster-aware redis-cli:
  docker exec -it a-master-temp-redis redis-cli -c -p 6379

To stop the nodes:
  docker stop ${CONTAINERS[*]}

To remove everything and rebuild the cluster:
  RESET_REDIS_CLUSTER=true $0
EOF_MSG
