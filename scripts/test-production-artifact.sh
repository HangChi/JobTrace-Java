#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
run_id="${$}"
image="jobtrace-production-smoke:$run_id"
network="jobtrace-production-smoke-$run_id"
database="jobtrace-production-db-$run_id"
application="jobtrace-production-app-$run_id"

cleanup() {
  docker rm --force "$application" "$database" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  docker image rm "$image" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if ! docker info >/dev/null 2>&1; then
  printf 'Docker is required and its daemon is not reachable.\n' >&2
  exit 1
fi

cd "$repo_dir"
./gradlew packagedApplicationTest --no-daemon
docker build --tag "$image" .

[[ "$(docker image inspect --format '{{.Config.User}}' "$image")" == '10001:10001' ]]
docker run --rm --entrypoint sh "$image" -c \
  'command -v java >/dev/null && ! command -v node >/dev/null && ! command -v npm >/dev/null'

docker network create "$network" >/dev/null
docker run --detach \
  --name "$database" \
  --network "$network" \
  --network-alias postgres \
  --env POSTGRES_DB=jobtrace \
  --env POSTGRES_USER=jobtrace \
  --env POSTGRES_PASSWORD=jobtrace \
  postgres:17-alpine >/dev/null

for attempt in {1..30}; do
  if docker exec "$database" pg_isready -U jobtrace -d jobtrace >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    printf 'PostgreSQL did not become ready.\n' >&2
    exit 1
  fi
  sleep 1
done

docker run --detach \
  --name "$application" \
  --network "$network" \
  --publish 127.0.0.1::8080 \
  --env JOBTRACE_DATABASE_URL=jdbc:postgresql://postgres:5432/jobtrace \
  --env JOBTRACE_DATABASE_USERNAME=jobtrace \
  --env JOBTRACE_DATABASE_PASSWORD=jobtrace \
  "$image" >/dev/null

host_port=$(docker port "$application" 8080/tcp | sed 's/.*://')
base_url="http://127.0.0.1:$host_port"

for attempt in {1..60}; do
  if curl --fail --silent "$base_url/api/health/live" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 60 ]]; then
    docker logs "$application" >&2
    printf 'Packaged application did not become ready.\n' >&2
    exit 1
  fi
  sleep 1
done

shell=$(curl --fail --silent "$base_url/")
deep_link=$(curl --fail --silent --header 'Accept: text/html' "$base_url/applications/example")
liveness=$(curl --fail --silent "$base_url/api/health/live")
readiness=$(curl --fail --silent "$base_url/api/health/ready")

[[ "$shell" == *'<div id="root"></div>'* ]]
[[ "$deep_link" == *'<div id="root"></div>'* ]]
[[ "$liveness" == '{"status":"ok"}' ]]
[[ "$readiness" == '{"status":"ok"}' ]]

printf 'Production artifact verification passed.\n'
