# Quickstart: Validate the Private Reminder Read Model

Feature 006 is currently a plan, not an implemented Java route. Use this guide after implementation to collect local and CI evidence without a production account, email send, delivery job, or deployment.

## Prerequisites

- Java 21 and the repository's Maven wrapper.
- Docker for isolated PostgreSQL 17 Testcontainers tests.
- Synthetic reminder, owner, application, profile, and notification-attempt fixtures only.
- No production database credentials or running legacy scheduler.

## Full validation

From the Java repository root:

```sh
./mvnw verify
```

Expected: all backend, bridge, contract, PostgreSQL isolation, architecture, Checkstyle, coverage, and performance gates pass; the React/TypeScript frontend and Java-only production artifact still build. Changed production code must reach at least 80% line and branch coverage. Required PR CI/security checks must pass before merge.

## Scenarios

1. **Active overview**: Seed due, future, and elapsed-but-stored-pending reminders for two owners. Verify due/upcoming classification at a fixed instant, stable notification-time/ID order, exactly the established 200-item cap, empty `history`, and no foreign rows or application names.
2. **History**: Request completed and cancelled selections separately. Verify only the selected status appears in `history`, active groups are empty, missing/unknown selection uses active, and nullable lifecycle timestamps match synthetic legacy responses.
3. **Current notification state and email**: Check absent, failed, and old-schedule attempts; only the current schedule's email state is exposed. Verify an unverified or removed recovery address produces `available: false` and `address: null`.
4. **Preferences**: Compare custom values and established defaults, with two-owner isolation and no settings update.
5. **Identity and failures**: For both GET routes, reject absent, forged, expired, path-mismatched, replayed, and ordinary-principal identity; assert safe problems, request IDs, no-store success headers, and private-data-free logs/metrics on storage failure.
6. **Performance and ownership**: Warm a representative fixture, measure at least 40 reads per operation, assert each p95 ≤ 500 ms and a fixed query-count ceiling. Confirm no new production migration, write route, scheduler, email integration, frontend routing, or deployment.

The response shape and protected routes are in [contracts/openapi.yaml](contracts/openapi.yaml); projection and ownership rules are in [data-model.md](data-model.md). Record any drift between the older legacy OpenAPI file and current runtime fixtures as described in [research.md](research.md).
