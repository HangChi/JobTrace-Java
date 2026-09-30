#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)

required_files=(
  "compose.yaml"
  "frontend/src/shared/api/health.ts"
  "src/test/java/com/jobtrace/shared/health/HealthControllerIntegrationTest.java"
  "specs/001-java-migration/quickstart.md"
)

for required_file in "${required_files[@]}"; do
  if [[ ! -f "$repo_dir/$required_file" ]]; then
    printf 'Missing quickstart requirement: %s\n' "$required_file" >&2
    exit 1
  fi
done

if ! docker info >/dev/null 2>&1; then
  printf 'Docker is required and its daemon is not reachable. Start Docker and retry.\n' >&2
  exit 1
fi

cd "$repo_dir"
./gradlew clean check jacocoTestCoverageVerification bootJar --no-daemon

(
  cd frontend
  npm ci
  npm run lint
  npm run test:coverage
  npm run build
)

jar_file="$repo_dir/build/libs/jobtrace-0.1.0-SNAPSHOT.jar"
jar tf "$jar_file" | grep -q 'BOOT-INF/classes/static/index.html'

printf 'Quickstart verification passed.\n'
