# Configuration

JobTrace Java reads configuration from Spring Boot property sources. Environment variables take precedence over committed defaults.

## Required runtime values

| Variable | Purpose | Secret |
|---|---|---:|
| `JOBTRACE_DATABASE_URL` | JDBC PostgreSQL URL | No, but may reveal infrastructure |
| `JOBTRACE_DATABASE_USERNAME` | PostgreSQL role | Yes |
| `JOBTRACE_DATABASE_PASSWORD` | PostgreSQL credential | Yes |
| `JOBTRACE_FLYWAY_ENABLED` | Allows schema migration execution | No; default must remain `false` during legacy adoption |
| `JOBTRACE_AUTH_BRIDGE_ENABLED` | Enables the internal signed identity bridge | No; defaults to `false` |
| `JOBTRACE_AUTH_BRIDGE_ISSUER` | Exact accepted legacy issuer | No |
| `JOBTRACE_AUTH_BRIDGE_AUDIENCE` | Exact Java service audience | No |
| `JOBTRACE_AUTH_BRIDGE_MAX_LIFETIME_SECONDS` | Maximum assertion lifetime, from 1 through 30 seconds | No |
| `JOBTRACE_AUTH_BRIDGE_CLOCK_SKEW_SECONDS` | Accepted clock skew, from 0 through 5 seconds | No |
| `JOBTRACE_AUTH_BRIDGE_REPLAY_KEY_PREFIX` | Non-secret namespace for replay records | No |
| `JOBTRACE_AUTH_BRIDGE_KEYS_0_ID` | Current accepted signing key identifier | No |
| `JOBTRACE_AUTH_BRIDGE_KEYS_0_SECRET` | Current base64url signing key, at least 32 random bytes | Yes |
| `JOBTRACE_AUTH_BRIDGE_KEYS_1_ID` | Previous key identifier during rotation | No |
| `JOBTRACE_AUTH_BRIDGE_KEYS_1_SECRET` | Previous base64url signing key during rotation | Yes |
| `JOBTRACE_REDIS_URL` | Shared replay-store connection URL | Yes |

## Local development

Use `.env.example` only as a field reference. Export values in the shell, use an ignored local environment file through your chosen runner, or configure the IDE. Spring Boot does not automatically load `.env` files.

Never place real credentials in `application.yaml`, Maven settings, npm configuration, test fixtures, or command output committed to Git.

The bridge remains disabled unless explicitly enabled. When enabled, it requires at least one
complete key pair and shared replay storage reachable by every Java replica. Empty placeholder key
entries are ignored while disabled. Add the new key before switching the legacy issuer, retain the
previous key for at least 35 seconds, then remove it. Never print a signing key or assertion.

## Precedence

From highest to lowest priority for this repository:

1. Command-line Spring properties.
2. Environment variables.
3. Profile-specific configuration explicitly added by a future specification.
4. Safe defaults in `src/main/resources/application.yaml`.

Production should use the platform secret manager. CI integration tests should use disposable Testcontainers credentials rather than shared databases.

## Database safety

- Use JDBC URLs beginning with `jdbc:postgresql://`.
- Keep Flyway disabled for every legacy database until `docs/database-baseline.md` is approved.
- Development credentials must not grant access to production.
- Read-only migration pilots should use a database role without schema privileges where practical.
