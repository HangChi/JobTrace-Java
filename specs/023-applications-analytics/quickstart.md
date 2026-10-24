# Verification and rollback

Run focused application and analytics tests, then `./mvnw verify` from the Java repository. Exercise exact HTTP routes with a verified session or signed bridge identity; never supply a public owner header. Check a second owner and stale version against each write class. Measure representative report reads and writes locally against fixture data.

The old service still owns production routes. Java code can be reverted or its routes disabled without reversing database migrations. Do not change frontend, CI/CD, deployment or traffic routing in this stage.

Verification evidence (2026-10-07): focused request, HTTP, PostgreSQL, report-rule and architecture tests passed. `./mvnw verify -q -Dlogging.level.root=ERROR` passed with 621 tests, 0 failures, 0 errors and 1 skipped. Aggregate JaCoCo coverage was 94.98% lines and 80.75% branches. The 023 added production classes alone reached 95.96% lines and 82.52% branches.

The repeatable local p95 samples used five warm-ups and twenty measured MockMvc requests against Testcontainers PostgreSQL: report GET was 1 ms with four application fixture rows; status PATCH was 0 ms with three fixture rows. These small-fixture numbers are regression signals, not production-load estimates. The budgets are 500 ms for reads and 1 s for writes. Production traffic is not activated in this stage.
