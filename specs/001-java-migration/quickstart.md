# Quickstart: Java Migration Foundation

## Prerequisites

- Java 21
- Node.js 24 or later for frontend development and builds
- PostgreSQL 17 for runtime readiness checks

No global Gradle installation is required.

## Verify the backend

```bash
./gradlew test
```

Expected result: all JUnit tests pass and a JaCoCo report is written under `build/reports/jacoco/test/html/`.

## Verify the frontend

```bash
cd frontend
npm ci
npm run lint
npm test
npm run build
```

Expected result: static assets are produced under `frontend/dist/`.

## Configure a local database

Copy `.env.example` to an ignored local environment file or export its variables in your shell. Use a JDBC-form PostgreSQL URL. Keep `JOBTRACE_FLYWAY_ENABLED=false` until the legacy baseline task is approved.

## Run in development

Terminal one:

```bash
./gradlew bootRun
```

Terminal two:

```bash
cd frontend
npm run dev
```

Open the Vite URL printed in terminal two. Requests under `/api` are proxied to Spring Boot on port 8080.

Verify:

```bash
curl http://localhost:8080/api/health/live
curl http://localhost:8080/api/health/ready
```

Liveness should return `{"status":"ok"}` without using the database. Readiness succeeds only when PostgreSQL is reachable.

## Build the production artifact

```bash
./gradlew bootJar
java -jar build/libs/jobtrace-0.1.0-SNAPSHOT.jar
```

`bootJar` runs the frontend production build and embeds `frontend/dist` in the executable JAR. The running production service does not require Node.js.

## Migration safety check

Before pointing the service at any existing JobTrace database, confirm:

1. `JOBTRACE_FLYWAY_ENABLED` is absent or `false`.
2. The connection uses a non-production database for development.
3. The selected migration slice has a contract baseline and one declared writer.
4. A rollback route has been exercised.

