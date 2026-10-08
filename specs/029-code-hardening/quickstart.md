# Validation: Code Hardening

1. Run `./mvnw verify` in the Java repository against the local synthetic PostgreSQL/Redis fixtures. Do not point tests at production data.
2. Review [route matrix](contracts/route-matrix.md): each legacy API route is supported or marked legacy-only; Java must not contain ATS/WeChat crawler or sync-run writer routes.
3. Run targeted security/error regressions: anonymous, cross-owner, ordinary-user admin, forged bridge, wrong-origin browser, missing CSRF, malformed JSON, missing parameters and dependency failure.
4. Run concurrent command regressions: one version or once-only transition cannot commit twice, and stale requests return a safe outcome without partial writes.
5. Check `jobtrace.migration.flyway-enabled=false`, absence of `src/main/resources/db/migration`, and no `flyway_schema_history` after synthetic startup. No baseline approval is implied.
6. Hand the user the following deferred live/manual checks; do not claim they were performed in this stage:
   - Sign in, reload a protected page, sign out and confirm the old cookie/session still works.
   - Create an application, change status and stage, and confirm analytics and reminders show the same record.
   - Import a sample CSV/XLSX, preview first, select rows, confirm once and check duplicate handling.
   - Create/edit a private interview, publish only completed content, then like/comment from another account.
   - As a regular user, try a direct `/admin` URL; as an administrator, inspect users, audit and shared marketplace data.
   - Confirm Java shows existing remote marketplace records but does not start ATS/WeChat collection or sync runs.

No CI/CD modification, production deployment, traffic cutover or remote database write is included.
