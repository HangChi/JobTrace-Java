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
target/jobtrace-0.1.0-SNAPSHOT.jar
```

Its manifest records `Build-Revision` and `Frontend-Asset-SHA256`. Inspect them
without starting the application:

```bash
unzip -p target/jobtrace-0.1.0-SNAPSHOT.jar META-INF/MANIFEST.MF
```

## Runtime configuration

Supply secrets through the deployment platform. Do not bake them into the image.
The required database settings are:

- `JOBTRACE_DATABASE_URL`
- `JOBTRACE_DATABASE_USERNAME`
- `JOBTRACE_DATABASE_PASSWORD`

The signed identity bridge remains off unless all of these are configured:

- `JOBTRACE_AUTH_BRIDGE_ENABLED=true`
- `JOBTRACE_AUTH_BRIDGE_ISSUER=legacy-jobtrace`
- `JOBTRACE_AUTH_BRIDGE_AUDIENCE=jobtrace-java`
- `JOBTRACE_AUTH_BRIDGE_KEYS_0_ID` and `JOBTRACE_AUTH_BRIDGE_KEYS_0_SECRET`
- `JOBTRACE_REDIS_URL` for a shared Redis-compatible replay store

Expose the Java analytics route only to the legacy service over the trusted
internal network. Ingress must reject browser and public network access. Never
forward browser cookies or public identity headers to Java.

Keep `JOBTRACE_FLYWAY_ENABLED=false` while the legacy database baseline is not
approved. Expose port 8080 and route probes as follows:

- liveness: `GET /api/health/live`
- readiness: `GET /api/health/ready`

Readiness must be removed from load-balancer rotation when it returns a non-200
status. Liveness should only restart a process that cannot respond.

## Deploy

Build the JAR and image from one immutable source revision:

```bash
./mvnw clean package
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

### Analytics canary rollback

The legacy deployment controls analytics routing with
`ANALYTICS_JAVA_CANARY_ENABLED` and `ANALYTICS_JAVA_CANARY_PERCENT`. On any
contract mismatch, replay/unknown-key spike, owner-isolation failure, error-rate
increase, or p95 above 500 ms:

1. Set `ANALYTICS_JAVA_CANARY_ENABLED=false` (or percentage to `0`) and deploy.
2. Confirm all analytics responses come from legacy within five minutes.
3. Leave the Java route deployed but unreachable from public ingress.
4. Record only revision, request ID, status class, mismatch class, and duration.

No database repair is needed because Java has no write ownership.

### Signing-key rotation

1. Generate a random 32-byte base64url secret in the secret manager with a new
   versioned key ID.
2. Add the new ID/secret to Java while retaining the previous key and verify
   readiness.
3. Change the legacy `IDENTITY_BRIDGE_ACTIVE_KEY_ID` and
   `IDENTITY_BRIDGE_ACTIVE_SECRET` to the new key.
4. Verify current and previous fixtures, then wait at least 35 seconds.
5. Remove the previous Java key and prove that it is rejected.

Alert on `jobtrace.auth.bridge.requests` failures by bounded reason and on
`jobtrace.auth.bridge.duration` p95. Key material, assertions, owner IDs, and
response bodies must never be logged.
