# Quickstart: Java Migration Foundation

## Prerequisites

- Java 21
- Node.js 24 or later
- Docker Engine or Docker Desktop with Docker Compose

No global Gradle or PostgreSQL installation is required. The production JAR does
not require Node.js; Node is only used to build and develop the browser application.

## Verify a clean clone

From the repository root, run the same backend, frontend, coverage, and packaging
checks used by CI:

```bash
./scripts/test-quickstart.sh
```

Expected result: the command ends with `Quickstart verification passed.` and the
JAR at `build/libs/jobtrace-0.1.0-SNAPSHOT.jar` contains the compiled frontend.
Docker must be running because the backend integration tests use Testcontainers.

## Prepare local development

Start PostgreSQL, wait for its health check, and install exact frontend dependencies:

```bash
./gradlew devSetup
```

The defaults in `compose.yaml` and `application.yaml` agree on these local-only
values:

```text
JOBTRACE_DATABASE_URL=jdbc:postgresql://127.0.0.1:5432/jobtrace
JOBTRACE_DATABASE_USERNAME=jobtrace
JOBTRACE_DATABASE_PASSWORD=jobtrace
JOBTRACE_FLYWAY_ENABLED=false
```

Copy `.env.example` when an external launcher needs an environment file. Spring
Boot already has the displayed development defaults, so ordinary local startup
does not require exporting them.

If port 5432 is occupied, start PostgreSQL on another host port and give Spring
Boot the matching JDBC URL:

```bash
JOBTRACE_POSTGRES_PORT=55432 ./gradlew devDatabaseUp
JOBTRACE_DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/jobtrace ./gradlew bootRun
```

Keep `JOBTRACE_FLYWAY_ENABLED=false` until the legacy schema baseline is approved.

## Run both applications

In terminal one:

```bash
./gradlew bootRun
```

In terminal two:

```bash
./gradlew frontendDev
```

Open the Vite URL printed in terminal two. Vite proxies `/api` requests to Spring
Boot on port 8080.

Verify the backend directly:

```bash
curl --fail http://localhost:8080/api/health/live
curl --fail http://localhost:8080/api/health/ready
```

With PostgreSQL healthy, both commands return:

```json
{"status":"ok"}
```

Liveness does not access PostgreSQL. Readiness returns HTTP 503 with
`{"status":"error"}` when the configured database cannot be reached.

Stop the development database without deleting its named data volume:

```bash
./gradlew devDatabaseDown
```

## Run quality gates separately

Backend tests, static analysis, and coverage:

```bash
./gradlew clean check jacocoTestCoverageVerification
```

Frontend lint, tests with coverage, and production build:

```bash
cd frontend
npm ci
npm run lint
npm run test:coverage
npm run build
```

## Build the production artifact

```bash
./gradlew bootJar
java -jar build/libs/jobtrace-0.1.0-SNAPSHOT.jar
```

`bootJar` embeds `frontend/dist` in the executable JAR. The running artifact only
needs Java and a reachable PostgreSQL database.

## Migration safety check

Before pointing the service at any existing JobTrace database, confirm:

1. `JOBTRACE_FLYWAY_ENABLED` is absent or `false`.
2. Development uses a non-production database.
3. The selected migration slice has a contract baseline and one declared writer.
4. Its rollback route has been exercised.
