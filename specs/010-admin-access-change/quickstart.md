# Quickstart: Validate Administrator Access Changes

## Scope

Validate only the administrator account-access PATCH command. Do not alter production schema, frontend code, deployment, routing, or CI/CD configuration.

## Run

```bash
./mvnw -Dtest='com.jobtrace.admin.*Access*Test,com.jobtrace.identityaccess.BridgeAuthenticationFilterTest,com.jobtrace.ArchitectureTest' test
./mvnw verify
```

## Required Evidence

1. **Contract**: The OpenAPI document exposes one exact PATCH path with four actions, strict request constraints, success, validation, authentication, authorization, not-found, conflict, and dependency-failure responses.
2. **Successful transitions**: Promote, demote, disable, and enable produce the expected role/status/version, one audit event, and session deletion only for disable.
3. **Protected transitions**: Stale versions, incompatible state, missing self-confirmation, and last-active-administrator requests change no account state and return stable conflict codes.
4. **Idempotency**: Sequential and concurrent identical retries produce one mutation and one audit event; different command content with the same request ID conflicts.
5. **Security**: Only a current verified administrator assertion for the exact PATCH target succeeds. Invalid identities disclose no account data.
6. **Safety**: Problems are private/no-store and retain request IDs without reasons or database diagnostics. Metrics have bounded labels.
7. **Performance**: Reproducible commands/replays meet the 1 second p95 write budget.
8. **Boundaries**: No new production migration, session issuer, account-settings writer, frontend route, job-market administrator, deployment, routing, or CI/CD change exists.
