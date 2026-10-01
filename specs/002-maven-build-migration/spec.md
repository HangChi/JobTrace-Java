# Feature Specification: Maven Build Migration

**Feature Branch**: `main`

**Created**: 2026-10-01

**Status**: Complete — delivered by PR #10 at `abae384` and revalidated on 2026-10-01

**Input**: User description: "Replace Gradle with Maven and remove all Gradle-related files and references."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reproducible contributor build (Priority: P1)

A contributor can clone the repository and use the repository-provided Maven wrapper to compile,
test, check code quality, and run the Spring Boot application without installing a build tool
globally.

**Why this priority**: Every later migration step depends on a reliable local and CI build.

**Independent Test**: Starting from a clean clone with Java, Node.js, npm, and Docker available,
run the documented verification command and confirm all backend tests, static checks, and coverage
thresholds pass.

**Acceptance Scenarios**:

1. **Given** a clean checkout with no global Maven installation, **When** a contributor runs the
   wrapper verification command, **Then** compilation, tests, Checkstyle, and coverage validation
   complete successfully.
2. **Given** Docker is available, **When** integration tests run, **Then** PostgreSQL-backed tests
   execute with the same isolation and assertions as before the build migration.

---

### User Story 2 - Equivalent production artifact (Priority: P2)

A release engineer can produce and verify the same single executable Java artifact, including the
compiled browser application and immutable build metadata, through the Maven workflow.

**Why this priority**: Changing the build system must not change production packaging or introduce
a Node.js runtime requirement.

**Independent Test**: Build the production artifact, run the packaged-application smoke test, and
confirm the application shell, health endpoints, source revision, and frontend asset digest are
present and valid.

**Acceptance Scenarios**:

1. **Given** frontend sources and a clean build directory, **When** the production package command
   runs, **Then** it installs locked frontend dependencies, builds the frontend, embeds the assets,
   and produces one executable JAR.
2. **Given** the resulting JAR and a Java-only runtime, **When** the smoke verification runs,
   **Then** the browser shell and health routes work without Node.js installed in that runtime.

---

### User Story 3 - One supported build workflow (Priority: P3)

Contributors and automation see only Maven commands, files, dependency update configuration, and
documentation, so there is no ambiguity about which build workflow is authoritative.

**Why this priority**: Keeping two build systems would create drift and undermine reproducibility.

**Independent Test**: Search all tracked current configuration, scripts, and documentation and
confirm there are no Gradle executables, wrapper files, build definitions, cache settings, output
paths, or active command references.

**Acceptance Scenarios**:

1. **Given** the Maven workflow passes all gates, **When** the migration is finalized, **Then** all
   Gradle build and wrapper files are removed.
2. **Given** a contributor follows any current setup, operations, or migration guide, **When** they
   copy a build command, **Then** it uses the Maven wrapper and matches CI behavior.

### Edge Cases

- If Maven Central or the Node package registry is unavailable on the first run, the build must
  fail clearly without silently using incomplete outputs.
- If frontend compilation fails, no apparently valid production JAR may be published.
- If Git revision metadata is unavailable, the artifact must use an explicit deterministic
  fallback rather than fail unpredictably.
- If Docker is unavailable, the verification documentation must distinguish the missing
  prerequisite from an application test failure.
- Re-running a build without source changes must not include stale frontend assets from a previous
  build.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST provide a Maven wrapper that pins the supported Maven version.
- **FR-002**: The Maven workflow MUST preserve Java 21 compilation and the existing dependency set.
- **FR-003**: The Maven verification lifecycle MUST execute backend tests, Checkstyle, and at least
  80% line and branch coverage gates with the current exclusions preserved.
- **FR-004**: The production package lifecycle MUST build the locked React/TypeScript frontend and
  embed its output in the executable Spring Boot JAR.
- **FR-005**: The executable JAR MUST retain the source revision and deterministic frontend asset
  SHA-256 manifest attributes.
- **FR-006**: The packaged-application smoke test MUST run against the Maven-produced executable JAR.
- **FR-007**: Development commands MUST continue to support starting PostgreSQL, running Spring
  Boot, and running the Vite development server through clearly documented commands.
- **FR-008**: CI and dependency update automation MUST use Maven dependency resolution and caching.
- **FR-009**: Docker packaging and verification scripts MUST consume Maven output paths.
- **FR-010**: Current repository guidance, operational documentation, migration documentation, and
  agent instructions MUST use Maven terminology and wrapper commands.
- **FR-011**: Gradle build definitions, wrapper executables, wrapper binaries, cache configuration,
  editor rules, and active repository references MUST be removed after Maven equivalence passes.
- **FR-012**: The migration MUST NOT alter application API behavior, database ownership, frontend
  behavior, accessibility conformance, or production runtime dependencies. Existing automated
  accessibility checks MUST remain passing under the Maven workflow.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A clean clone completes the documented full verification workflow in under 20 minutes
  on the supported development environment.
- **SC-002**: 100% of existing backend and frontend tests pass under the replacement workflow.
- **SC-003**: Line and branch coverage remain at or above 80% with no weakened exclusions or rules.
- **SC-004**: The production artifact smoke test passes in a Java-only runtime and validates all
  required embedded assets and metadata.
- **SC-005**: A tracked-file scan reports zero active Gradle files, commands, cache settings, or
  build-output references after migration.
- **SC-006**: CI passes backend, frontend, and production-artifact jobs on both the pull request and
  the merged main branch.

## Assumptions

- Java 21, Node.js 24, npm lockfile usage, PostgreSQL, Docker, Spring Boot, React, TypeScript, and
  Vite remain unchanged.
- The project remains a single backend module with one nested frontend project.
- The repository-provided wrapper is the supported entry point; a global Maven installation is not
  required for contributors or CI.
- Historical Git commits may mention Gradle; the zero-reference requirement applies to the tracked
  contents of the current revision.
