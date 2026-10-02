# Feature 005: private interview read model

Feature 005 adds three owner-scoped, read-only Java endpoints: `GET /api/interviews`, `GET /api/interviews/{id}`, and `GET /api/applications/{id}/detail`. They use the feature 003 signed assertion bridge and the existing application detail use case from feature 004. The bridge is disabled by default. No Java production traffic is routed here.

The existing JobTrace service remains the sole owner of browser sessions, business writes, review publication/engagement, and the PostgreSQL schema and migration history. Java reads the unchanged legacy tables through parameterized Spring JDBC; no production Flyway migration or write handler is added. Public feeds/details, view counts, likes, comments, exports, and review/application mutations remain in the existing service.

## Contract and isolation

- Only matching `BridgeIdentity` details supply the owner. Public owner headers and ordinary authenticated principals are rejected.
- Every list, total, detail, child, and application-summary query binds the trusted owner. Application dialog checks application ownership before reading interviews. Missing and cross-owner identifiers share the same status and stable problem code.
- The private list retains strict legacy filters, inclusive date bounds, full filtered total before cursor navigation, and `(interviewed_on DESC, id DESC)` order. Cursors are navigation data, not authorization.
- Detail questions and actions retain saved order. Removed stage links fall back to the review's stored stage snapshot. `private, no-store` is emitted on successful reads.
- Safe problems carry a request ID. Metrics have only three bounded operation names and six bounded outcomes; they do not label owner, review, query, or token.

## Local validation

Run `./mvnw verify` with Java 21 and Docker. The test-only PostgreSQL 17 schema and synthetic fixtures under `src/test/resources/` reproduce the legacy read model without production credentials. Contract tests compare complete JSON structure; integration tests cover owner isolation, filters, cursor ties, ordered children, and dialog composition. The fixed-load performance test uses 103 owned reviews, 10 warm-ups and 40 samples per operation; it asserts p95 ≤ 500 ms and query counts of 2 for list, 3 for owned detail, and 4 for dialog.

Passing local and CI checks is the only completion criterion for this slice. Deployment, cutover, canary, observation and rollback rehearsal require a separate future release decision. Until such a decision, the existing service continues serving all production traffic.
