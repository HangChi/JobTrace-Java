# Research: Maven Build Migration

## Maven and wrapper versions

**Decision**: Pin Apache Maven 3.9.16 through Apache Maven Wrapper 3.3.4 using the script-only
wrapper type.

**Rationale**: Maven 3.9.16 is the current stable Maven 3 release and Maven 4 remains a preview.
Wrapper 3.3.4 is the current stable wrapper and its script-only mode avoids committing a binary
bootstrap JAR while still removing the global Maven prerequisite.

**Alternatives considered**:

- Maven 4 release candidates: rejected because they are explicitly not production-stable.
- A globally installed Maven prerequisite: rejected because it weakens clean-clone reproducibility.
- Binary wrapper JAR: valid, but unnecessary when the official script-only wrapper is available.

Primary references: [Apache Maven downloads](https://maven.apache.org/download.cgi),
[Apache Maven Wrapper](https://maven.apache.org/tools/mavenwrapper.html).

## Lifecycle and frontend integration

**Decision**: Run locked npm installation and the Vite production build before Maven resource
processing, then copy `frontend/dist` into the application class output so Spring Boot repackages it
inside the executable JAR.

**Rationale**: This preserves the current single-artifact behavior and prevents a package from
succeeding with stale or missing frontend assets. Using the system Node.js/npm already required by
the repository avoids downloading a second Node runtime through Maven.

**Alternatives considered**:

- Build frontend only in CI scripts: rejected because local `package` could produce a different JAR.
- Commit frontend output: rejected because generated assets would drift from their sources.
- Download Node through a Maven plugin: rejected because Node 24 is already an explicit environment
  prerequisite and CI installs it separately.

## Quality gates

**Decision**: Bind Checkstyle and JaCoCo report/check to Maven `verify`, with the existing 80% line
and branch thresholds and exclusions unchanged. Use Surefire for unit/integration tests and a
profile-bound Failsafe execution for the already existing packaged-application smoke test.

**Rationale**: `verify` becomes the single backend release gate and preserves the lowest effective
test levels. The packaged test must execute after the Spring Boot JAR exists, which aligns with the
integration-test/verify phases.

**Alternatives considered**:

- Move packaged verification to an external script only: rejected because the artifact contract
  should remain enforceable by the build lifecycle.
- Weaken or change coverage exclusions: rejected because the migration must be behavior-neutral.

## Build metadata

**Decision**: Generate the Git source revision and an aggregate SHA-256 of the compiled frontend
assets during `prepare-package`, export them as Maven properties, and write them into the JAR
manifest.

**Rationale**: The production smoke test already verifies both manifest entries. Computing metadata
after a fresh frontend build preserves deterministic traceability without adding runtime code.

**Alternatives considered**:

- Drop manifest metadata: rejected because it weakens release traceability.
- Hash only `index.html`: rejected because it would not identify changes to bundled JS/CSS assets.

## Development orchestration

**Decision**: Replace custom Gradle development tasks with direct, documented commands:
`docker compose`, `./mvnw spring-boot:run`, and `npm run dev`.

**Rationale**: Each process remains visible, independently restartable, and portable. Maven remains
the Java build entry point without becoming a general-purpose process supervisor.

**Alternatives considered**:

- Recreate every task as a Maven plugin execution: rejected because long-running Docker and Vite
  processes do not fit the Maven lifecycle cleanly.
- Add Make: rejected because it would introduce another orchestration dependency.
