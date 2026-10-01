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

## Convergence revalidation

The complete current quickstart was rerun from the closeout branch on 2026-10-01 with Java 21.0.12,
Maven Wrapper 3.9.16, Node.js 24, and Docker Desktop:

| Check | Result | Evidence |
|---|---|---|
| Full quickstart duration | PASS | `./scripts/test-quickstart.sh` completed in 29.21 seconds, below the 20-minute SC-001 budget |
| Backend | PASS | 39 tests, 0 failures, 0 errors, 1 expected packaged-profile skip; JaCoCo and Checkstyle passed |
| Frontend | PASS | 12 tests passed; 100% statements/functions/lines and 84.61% branches; lint and build passed |
| Accessibility | PASS | The existing axe-based migration-shell accessibility test remained part of the passing frontend suite; no UI behavior changed |
| Core Web Vitals | PASS | Chromium lab run reported LCP 72 ms, INP 16 ms, and CLS 0 |
| Maven-only workflow | PASS | No tracked Gradle files or active Gradle commands, cache settings, or `build/libs` references remain outside this historical migration record |

The first sandboxed attempt stopped immediately because the Docker socket was isolated. The same
command passed when run with access to the local Docker daemon; this was an environment prerequisite,
not an application failure.

## Remote delivery evidence

- [PR #10](https://github.com/HangChi/JobTrace-Java/pull/10) merged the Maven migration into `main`
  as `abae38414752032f90690ea84d636a5ac53f5520` on 2026-10-01 and reports all three migration CI
  checks passed.
- The later `main` revision `bc261b54c4647623e587e59cc6b426adf55b81e4` revalidated the same Maven
  workflow: backend, frontend, production-artifact, dependency-review, and secret-scan all passed.
- Branch protection continues to require the five documented CI and security checks before merge.

## Solo-maintainer self-review

The repository has exactly one human maintainer with write access, so Constitution v1.1.0
Solo-Maintainer Mode applies. The closeout change was reviewed against this specification and all
four constitutional principles:

| Review area | Result | Assessment |
|---|---|---|
| Specification | PASS | Maven is the sole supported build, all FR-001–FR-012 and SC-001–SC-006 outcomes have implementation and validation evidence |
| I. Maintainable Code | PASS | One pinned Maven lifecycle replaces duplicate build authority; no new runtime layer or dependency is introduced |
| II. Testing | PASS | Backend, frontend, accessibility, coverage, packaging, Docker, performance, dependency, and secret gates remain enforced |
| III. UX and accessibility | PASS | The migration changes no UI behavior and the existing axe accessibility test remains passing |
| IV. Performance | PASS | The full workflow completes in 29.21 seconds and current API and Core Web Vitals budgets remain passing |

The closeout pull request must retain this self-review, pass every required check, and have no
unresolved conversations before merge. If a second human receives write access, at least one
independent approval becomes mandatory before the next production merge.
