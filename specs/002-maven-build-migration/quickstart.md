# Quickstart: Validate the Maven build migration

## Prerequisites

- Java 21
- Node.js 24 and npm
- Docker with a reachable daemon
- Git

A global Maven installation is not required.

## 1. Confirm the wrapper

```bash
./mvnw --version
```

Expected: Maven 3.9.16 and Java 21 are reported.

## 2. Run the backend release gate

```bash
./mvnw clean verify
```

Expected: compilation, Checkstyle, JUnit/Testcontainers tests, JaCoCo reporting, and 80% line and
branch coverage checks pass. The build also compiles fresh frontend assets for the executable JAR.

## 3. Run frontend-specific gates

```bash
cd frontend
npm ci
npm run lint
npm run test:coverage
npm run build
cd ..
```

Expected: lint, 80% coverage thresholds, TypeScript compilation, and the Vite production build pass.

## 4. Verify the executable artifact

```bash
./mvnw -Ppackaged-test clean verify
```

Expected: `target/jobtrace-0.1.0-SNAPSHOT.jar` starts successfully, serves the application shell and
health endpoints, embeds `index.html`, and contains valid `Build-Revision` and
`Frontend-Asset-SHA256` manifest entries.

## 5. Verify the Java-only production image

```bash
./scripts/test-production-artifact.sh
```

Expected: the production image contains Java but not Node.js/npm, starts against PostgreSQL, and
serves the browser shell, deep links, liveness, and readiness routes.

## 6. Run development processes

Terminal 1:

```bash
docker compose up --detach --wait postgres
```

Terminal 2:

```bash
./mvnw spring-boot:run
```

Terminal 3:

```bash
cd frontend
npm ci
npm run dev
```

Stop PostgreSQL with `docker compose down`.

## 7. Confirm Gradle removal

```bash
git ls-files | grep -Ei '(^|/)(gradle|gradlew)|build\.gradle|settings\.gradle' && exit 1 || true
git grep -nEi 'gradle|gradlew|bootJar|build/libs' -- ':!specs/002-maven-build-migration/**' && exit 1 || true
```

Expected: both scans produce no active matches. The migration record itself is excluded because it
documents the retired workflow and its removal evidence.
