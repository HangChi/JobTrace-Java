# Configuration

JobTrace Java reads configuration from Spring Boot property sources. Environment variables take precedence over committed defaults.

## Required runtime values

| Variable | Purpose | Secret |
|---|---|---:|
| `JOBTRACE_DATABASE_URL` | JDBC PostgreSQL URL | No, but may reveal infrastructure |
| `JOBTRACE_DATABASE_USERNAME` | PostgreSQL role | Yes |
| `JOBTRACE_DATABASE_PASSWORD` | PostgreSQL credential | Yes |
| `JOBTRACE_FLYWAY_ENABLED` | Allows schema migration execution | No; default must remain `false` during legacy adoption |
| `JOBTRACE_AUTH_BRIDGE_SECRET` | Future server-to-server identity signature key | Yes; unused until the bridge specification is implemented |

## Local development

Use `.env.example` only as a field reference. Export values in the shell, use an ignored local environment file through your chosen runner, or configure the IDE. Spring Boot does not automatically load `.env` files.

Never place real credentials in `application.yaml`, Maven settings, npm configuration, test fixtures, or command output committed to Git.

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
