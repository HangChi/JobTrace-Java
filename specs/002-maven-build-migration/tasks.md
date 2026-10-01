# Tasks: Maven Build Migration

**Input**: Design documents from `specs/002-maven-build-migration/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`

**Tests**: Existing backend, frontend, packaged-artifact, and Docker smoke tests are preserved and
used as equivalence gates. Script changes are validated before Gradle removal.

**Organization**: Tasks are grouped by independently testable contributor, release, and cleanup stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes independent files with no incomplete dependency
- **[Story]**: Maps the task to a user story from `spec.md`

## Phase 1: Setup

**Purpose**: Establish the pinned Maven entry point without changing application behavior.

- [x] T001 Add the script-only Maven 3.9.16 wrapper in `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/maven-wrapper.properties`
- [x] T002 Create the Maven project coordinates, Java 21 baseline, and dependency parity in `pom.xml`

---

## Phase 2: Foundational

**Purpose**: Reproduce shared release gates before changing packaging or deleting Gradle.

**⚠️ CRITICAL**: User-story verification begins only after Maven can compile the unchanged source tree.

- [x] T003 Configure JUnit, Checkstyle, JaCoCo reports, 80% line/branch gates, and existing exclusions in `pom.xml`
- [x] T004 Verify all existing backend tests and static checks with `./mvnw test` and `./mvnw verify`

**Checkpoint**: Maven independently verifies the backend without Gradle.

---

## Phase 3: User Story 1 - Reproducible contributor build (Priority: P1) 🎯 MVP

**Goal**: Contributors can build, test, and run the backend through the Maven wrapper.

**Independent Test**: From a clean checkout, `./mvnw clean verify` passes without a global Maven installation.

### Implementation for User Story 1

- [x] T005 [US1] Replace the backend quickstart acceptance command and JAR assertion in `scripts/test-quickstart.sh`
- [x] T006 [P] [US1] Replace development and verification commands in `specs/001-java-migration/quickstart.md`
- [x] T007 [P] [US1] Replace contributor commands and build-tool guidance in `README.md` and `AGENTS.md`

**Checkpoint**: The contributor workflow is Maven-only and independently usable.

---

## Phase 4: User Story 2 - Equivalent production artifact (Priority: P2)

**Goal**: Maven produces and verifies the same frontend-embedded, traceable executable JAR.

**Independent Test**: `./mvnw -Ppackaged-test clean verify` and
`./scripts/test-production-artifact.sh` both pass.

### Implementation for User Story 2

- [x] T008 [US2] Add locked frontend compilation, resource embedding, source revision, asset SHA-256, and Spring Boot repackaging to `pom.xml`
- [x] T009 [US2] Bind the packaged application smoke test to the post-package Maven profile in `pom.xml`
- [x] T010 [P] [US2] Change the production JAR source from `build/libs` to `target` in `Dockerfile` and `.dockerignore`
- [x] T011 [US2] Replace the artifact build and smoke command in `scripts/test-production-artifact.sh`
- [x] T012 [P] [US2] Replace production build and rollback commands in `docs/operations.md` and `docs/architecture.md`

**Checkpoint**: The Java-only production artifact is behaviorally equivalent under Maven.

---

## Phase 5: User Story 3 - One supported build workflow (Priority: P3)

**Goal**: CI, automation, configuration, specifications, and documentation contain no active Gradle workflow.

**Independent Test**: A tracked-file and content scan finds no Gradle files, commands, cache settings,
or `build/libs` production references.

### Implementation for User Story 3

- [x] T013 [P] [US3] Change backend and production jobs to Maven caching and wrapper commands in `.github/workflows/ci.yml`
- [x] T014 [P] [US3] Change dependency updates from Gradle to Maven in `.github/dependabot.yml`
- [x] T015 [P] [US3] Replace build-output ignores and editor rules in `.gitignore`, `.dockerignore`, and `.editorconfig`
- [x] T016 [P] [US3] Replace active Gradle references in `docs/configuration.md`, `docs/migration-slices/analytics-summary.md`, and `specs/001-java-migration/`
- [x] T017 [US3] Delete `build.gradle.kts`, `settings.gradle.kts`, `gradlew`, `gradlew.bat`, and `gradle/` after Maven equivalence passes
- [x] T018 [US3] Add and run the Maven-only tracked-content assertion in `scripts/test-quickstart.sh`

**Checkpoint**: Maven is the only supported build workflow.

---

## Phase 6: Polish and validation

**Purpose**: Prove clean-clone and CI-equivalent behavior before merge.

- [x] T019 Run backend, frontend, packaged-JAR, and Docker quickstart validation from `specs/002-maven-build-migration/quickstart.md`
- [x] T020 Record successful commands and final removal evidence in `specs/002-maven-build-migration/validation-report.md`

---

## Dependencies and execution order

### Phase dependencies

- **Setup**: Starts immediately.
- **Foundational**: Depends on the wrapper and dependency model; blocks all stories.
- **US1**: Depends on a passing Maven backend lifecycle.
- **US2**: Depends on the Maven lifecycle and must pass before Gradle removal.
- **US3**: CI/docs edits can begin earlier, but T017 deletion waits for US2 equivalence.
- **Polish**: Depends on all three stories.

### User story dependencies

- **US1** proves contributor parity and can complete after Foundational.
- **US2** adds production packaging on top of the verified Maven backend.
- **US3** finalizes the single workflow only after US1 and US2 pass.

### Parallel opportunities

- T006 and T007 edit independent documentation.
- T010 and T012 are independent of the POM changes once the target path is fixed.
- T013–T016 update independent CI, automation, configuration, and documentation files.

## Parallel example: User Story 3

```text
Task: T013 Change CI to Maven in .github/workflows/ci.yml
Task: T014 Change Dependabot to Maven in .github/dependabot.yml
Task: T015 Replace ignore and editor rules in .gitignore, .dockerignore, and .editorconfig
Task: T016 Replace active Gradle documentation and specification references
```

## Implementation strategy

1. Introduce Maven alongside Gradle temporarily.
2. Make backend and quality gates pass under Maven.
3. Reproduce frontend embedding, manifest metadata, and packaged smoke tests.
4. Change scripts, CI, Docker, and documentation.
5. Delete Gradle only after Maven equivalence is proven.
6. Run the complete clean-clone validation and merge through CI.

## Format validation

All tasks use the required checkbox, sequential ID, optional parallel marker, required story label
inside user-story phases, an actionable description, and exact file paths.

---

## Phase 7: Convergence

**Purpose**: Close lifecycle, constitutional, and remote-validation gaps before declaring the Maven
build migration complete.

- [x] T021 CRITICAL Add an explicit accessibility non-regression expectation and evidence in `specs/002-maven-build-migration/spec.md` and `specs/002-maven-build-migration/validation-report.md` per Constitution III and delivery gate 1 (missing)
- [x] T022 CRITICAL Record the Solo-Maintainer Mode written self-review against the specification and all four constitutional principles in `specs/002-maven-build-migration/validation-report.md` and the closeout pull request per Constitution delivery gate 4 (missing)
- [x] T023 CRITICAL Document build and runtime observability expectations in `specs/002-maven-build-migration/plan.md` per Constitution delivery gate 2 (missing)
- [x] T024 Update the completed lifecycle status, branch metadata, completion date, and merge revision in `specs/002-maven-build-migration/spec.md` and `specs/002-maven-build-migration/plan.md` (partial)
- [x] T025 Measure the complete current quickstart duration and record evidence that it remains below 20 minutes in `specs/002-maven-build-migration/validation-report.md` per SC-001 (partial)
- [x] T026 Record PR #10 and merged-main CI evidence in `specs/002-maven-build-migration/validation-report.md` per SC-006 (partial)
- [x] T027 Correct the feature document tree in `specs/002-maven-build-migration/plan.md` to include `spec.md` and `validation-report.md` (partial)
