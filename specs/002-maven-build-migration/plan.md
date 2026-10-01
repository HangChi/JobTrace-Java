# Implementation Plan: Maven Build Migration

**Branch**: `codex/maven-build-migration` | **Date**: 2026-10-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-maven-build-migration/spec.md`

## Summary

Replace the single-project Gradle build with a pinned Maven Wrapper workflow while preserving the
Spring Boot dependency graph, Java 21 compilation, Checkstyle and JaCoCo gates, Testcontainers
tests, Vite frontend build, executable-JAR layout, build metadata, Docker image, and CI behavior.
Validate Maven equivalence first, then remove every Gradle file and active reference.

## Technical Context

**Language/Version**: Java 21; TypeScript 6; shell scripts for verification

**Primary Dependencies**: Spring Boot 4.1.1, Maven 3.9.16, Maven Wrapper 3.3.4, React 19,
Vite 8, PostgreSQL 17, Testcontainers 2.0.5

**Storage**: Existing PostgreSQL schema; no data or schema changes

**Testing**: JUnit 5, Spring Boot Test, Testcontainers, ArchUnit, JaCoCo, Checkstyle, Vitest,
Oxlint, executable-JAR smoke test, Docker production smoke test

**Target Platform**: Developer macOS/Linux/Windows with Java 21; GitHub Actions Ubuntu runners;
production Java 21 Alpine container

**Project Type**: Modular-monolith web application with a Spring Boot backend and nested Vite frontend

**Performance Goals**: Clean-clone verification in under 20 minutes; no runtime performance change

**Constraints**: Preserve 80% line and branch coverage; no global Maven requirement; no Node.js in
the production image; no stale frontend output; no API, persistence, or authentication behavior change

**Scale/Scope**: One root Java module, one nested frontend project, three CI jobs, two shell
verification scripts, and all current repository documentation/configuration references

## Constitution Check

*GATE: Passed before research and rechecked after design.*

- **Maintainable Code**: One authoritative build definition replaces the old one. Maven plugin
  configuration is explicit and version-pinned; development orchestration stays in transparent
  Docker/npm commands rather than hidden custom lifecycle behavior.
- **Testing Gate**: Existing tests are unchanged. Maven `verify` enforces Checkstyle plus 80% line
  and branch coverage. Packaged and Docker smoke tests remain required.
- **Consistent UX**: No application UI or behavior changes. The exact frontend build remains embedded.
- **Performance Budgets**: Runtime code is unchanged. Build completion is measured against the
  existing under-20-minute contributor target.
- **Delivery**: CI validates Maven on the pull request and main. Rollback is reverting one build-only
  commit; no database or runtime data migration exists.

Post-design recheck: passed. The design adds only standard Maven plugins needed to reproduce
existing gates and introduces no new runtime dependencies or architectural layers.

## Project Structure

### Documentation (this feature)

```text
specs/002-maven-build-migration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

No external API contract changes are required, so this feature has no `contracts/` directory.

### Source Code and build files (repository root)

```text
pom.xml
mvnw
mvnw.cmd
.mvn/
└── wrapper/
    └── maven-wrapper.properties

src/main/java/
src/main/resources/
src/test/java/
src/test/resources/

frontend/
├── package.json
├── package-lock.json
├── src/
└── dist/                    # generated, never committed

scripts/
├── test-quickstart.sh
└── test-production-artifact.sh

Dockerfile
.github/workflows/ci.yml
.github/dependabot.yml
```

**Structure Decision**: Keep the existing single Java module and nested frontend layout. `pom.xml`
owns backend compilation, quality gates, frontend production compilation, asset embedding, manifest
metadata, and executable-JAR repackaging. Local development runs Docker Compose, Spring Boot, and
Vite as separate documented processes.

## Complexity Tracking

No constitution violations require justification.
