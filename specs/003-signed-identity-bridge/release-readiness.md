# Analytics canary release preparation

**Preparation date:** 2026-10-26  
**Decision:** Awaiting production deployment approval. Java receives 0% production analytics traffic; legacy remains the traffic and write owner. `JOBTRACE_AUTH_BRIDGE_ENABLED=false` and `JOBTRACE_FLYWAY_ENABLED=false` remain the safe defaults.

## Evidence already available

- The Java bridge and legacy issuer passed the contract, replay, owner-isolation, rotation, parity, timeout and fallback checks in [validation-report.md](validation-report.md).
- Java PR [#15](https://github.com/HangChi/JobTrace-Java/pull/15) and legacy PR [#1](https://github.com/HangChi/JobTrace/pull/1) passed their recorded CI gates. Re-run the relevant gates on the exact revisions selected for release.
- The route-specific rollback and observation criteria are in [analytics-summary.md](../../docs/migration-slices/analytics-summary.md); the synthetic rehearsal is in [quickstart.md](quickstart.md).

## Approval record to fill before deployment

| Required field | Current value |
| --- | --- |
| Approved deployment environment and non-public Java ingress | Unset |
| Release approver and approval record | Unset |
| Traffic owner and on-call rollback executor | Unset |
| Exact Java and legacy release revisions | Unset |
| Secret-manager references for current and previous signing keys | Unset; record references, never key material |
| Shared Valkey/Redis replay-store endpoint and readiness evidence | Unset |
| Metrics/dashboard and alert links for mismatch, security, errors and p95 latency | Unset |
| Rollback rehearsal timestamp, result and executor | Unset |
| Canary start time, initial traffic share and scheduled seven-day end | Unset |

Do not infer approval from a merged PR, successful synthetic test, or a filled template. The approver must explicitly authorize the deployment and traffic share after reviewing the completed record.

## Preflight and release sequence

1. Pin the exact Java and legacy artifacts, confirm five required Java PR checks and the legacy CI gates on those revisions, and verify the signed v1 issuer/audience/key IDs match. Keep secrets in the secret manager and out of logs and the browser.
2. Confirm the bridge endpoint is reachable only from trusted legacy service ingress; public identity headers cannot establish a Java principal. Confirm all Java replicas use the same atomic replay store. Keep Flyway disabled and the legacy service as sole schema and marketplace-ingestion owner.
3. In the approved non-public environment, exercise valid, forged, expired, replayed and wrong-request assertions; owner isolation; normalized analytics comparison; key overlap/retirement; and a replay-store outage. Record request IDs and classifications without tokens, owner identifiers or response bodies.
4. Rehearse the route-specific rollback with synthetic traffic. The executor must restore 100% legacy routing and stop Java shadow comparison within five minutes, then verify legacy analytics and Java health. Record the real elapsed time and evidence link.
5. Only after explicit release approval, start a bounded production canary for `GET /api/analytics/summary`. Record the share, start time, traffic owner and dashboard links in [analytics-summary.md](../../docs/migration-slices/analytics-summary.md). Do not route write paths or other analytics endpoints to Java.
6. Observe seven consecutive real production days. Each day record the share, zero contract mismatches, zero identity/owner security incidents, no p95 budget breach (bridge validation 25 ms; analytics read 500 ms), error and dependency health, and evidence links. A gap or breach stops the window and requires a new approved start.
7. Roll back immediately on a mismatch, confirmed security incident, replay-store failure, sustained error/latency breach or loss of comparison visibility. Keep the legacy read and all writes authoritative. After seven clean days, record an explicit active or rolled-back decision and final traffic owner; T044 and T045 remain open until those facts exist.

## Current blockers

- Production deployment and traffic approval have not been granted; environment, owner and observability links are not yet recorded.
- The seven-day observation window has not started, so no production activation decision can be made.
- The separate Flyway baseline has not been approved and the legacy migration chain has a duplicate version; its disabled state is independent of this read-only canary.
