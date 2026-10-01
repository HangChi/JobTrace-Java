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

cd "$repo_dir"

legacy_file_pattern='(^|/)(gra''dle|gra''dlew)|build\.gra''dle|settings\.gra''dle'
legacy_reference_pattern='gra''dle|gra''dlew|boot''Jar|build/li''bs'

if git ls-files | grep -Eiq "$legacy_file_pattern"; then
  printf 'Legacy build-tool files are still tracked.\n' >&2
  exit 1
fi

if git grep -nEi "$legacy_reference_pattern" -- ':!specs/002-maven-build-migration/**'; then
  printf 'Legacy build-tool references are still active outside the migration record.\n' >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  printf 'Docker is required and its daemon is not reachable. Start Docker and retry.\n' >&2
  exit 1
fi

./mvnw clean verify

(
  cd frontend
  npm ci
  npm run lint
  npm run test:coverage
  npm run build
  npx playwright install chromium
  npm run test:performance
)

jar_file="$repo_dir/target/jobtrace-0.1.0-SNAPSHOT.jar"
jar tf "$jar_file" | grep -q 'BOOT-INF/classes/static/index.html'

printf 'Quickstart verification passed.\n'
