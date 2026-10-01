# Production operations

JobTrace ships as one executable Spring Boot JAR and one OCI image. React assets
are compiled during the build and embedded in the JAR; production requires Java
21 and PostgreSQL, but not Node.js or npm.

## Build and verify

Run the complete artifact verification before publishing a release:

```bash
./scripts/test-production-artifact.sh
```

The script starts the executable JAR, builds the runtime image, confirms that the
image contains Java but no `node` or `npm` executable, and checks the application
shell, a direct browser route, liveness, and database-backed readiness.

The artifact is written to:

```text
build/libs/jobtrace-0.1.0-SNAPSHOT.jar
```

Its manifest records `Build-Revision` and `Frontend-Asset-SHA256`. Inspect them
without starting the application:

```bash
unzip -p build/libs/jobtrace-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF
```

## Runtime configuration

Supply secrets through the deployment platform. Do not bake them into the image.
The required database settings are:

- `JOBTRACE_DATABASE_URL`
- `JOBTRACE_DATABASE_USERNAME`
- `JOBTRACE_DATABASE_PASSWORD`

Keep `JOBTRACE_FLYWAY_ENABLED=false` while the legacy database baseline is not
approved. Expose port 8080 and route probes as follows:

- liveness: `GET /api/health/live`
- readiness: `GET /api/health/ready`

Readiness must be removed from load-balancer rotation when it returns a non-200
status. Liveness should only restart a process that cannot respond.

## Deploy

Build the JAR and image from one immutable source revision:

```bash
./gradlew clean bootJar
revision="$(git rev-parse --short=12 HEAD)"
docker build --tag "jobtrace:$revision" .
```

Publish the image under an immutable digest. Before shifting traffic, compare the
image revision and frontend digest with the values reviewed in CI, then require
both health probes to pass against the intended PostgreSQL instance.

## Rollback

Keep the previously healthy image digest available throughout the observation
window. To roll back:

1. Stop routing new traffic to the failing revision.
2. Redeploy the previous immutable image digest with the same external settings.
3. Wait for liveness and readiness to return HTTP 200.
4. Restore traffic and record the failing source revision, frontend digest, and
   request IDs in the incident evidence.

The foundation release does not own schema changes, so application rollback does
not require a database migration. If a future release changes that rule, its own
migration specification must provide a tested data-restoration procedure.
