# Validation report: Maven build migration

**Date**: 2026-10-01

## Passed locally

- `./mvnw --version`: Maven 3.9.16 on Java 21.0.12.
- `./mvnw test`: 36 tests, 0 failures, 0 errors, 1 intentionally skipped packaged test.
- `./mvnw verify`: backend tests, JaCoCo 80% line/branch gates, and Checkstyle passed.
- `./mvnw -Ppackaged-test clean verify`: the executable-JAR smoke test passed in addition to all
  backend gates.
- `npm ci && npm run lint && npm run test:coverage && npm run build`: 11 frontend tests passed;
  statements, functions, and lines were 100%, and branches were 84.61%.
- The executable JAR contains `BOOT-INF/classes/static/index.html` and hashed Vite assets.
- The executable JAR manifest contains the full `Build-Revision` and
  `Frontend-Asset-SHA256` values.
- Maven-only tracked-file and content assertions passed after deleting every tracked Gradle file.
- `./scripts/test-quickstart.sh`: the aggregate Maven, frontend, coverage, and artifact check passed.
- `./scripts/test-production-artifact.sh`: the packaged test, non-root Java-only image, temporary
  PostgreSQL integration, browser shell/deep-link routes, liveness, and readiness checks passed.
- `bash -n` passed for both verification scripts, `xmllint --noout pom.xml` passed, and
  `git diff --check` reported no whitespace errors.

## Removal evidence

The repository no longer tracks `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradlew.bat`,
or `gradle/`. Active files outside this migration record contain no Gradle command, cache setting,
task name, or `build/libs` artifact reference. Maven Wrapper, `pom.xml`, CI, Dependabot, Docker,
scripts, contributor guidance, and operations documentation now describe one Maven workflow.
