# Analytics summary migration slice

| Field | Value |
| --- | --- |
| Slice | `analytics-summary` |
| Capability | `GET /api/analytics/summary` |
| State | `VERIFIED` (not production-active) |
| Legacy owner | `legacy-jobtrace` |
| Target owner | `jobtrace-java` shadow implementation |
| Write owner | `legacy-jobtrace` |
| Schema ownership | Legacy; no Java migrations |

## Contract evidence

The Java implementation reproduces the legacy response for empty and representative synthetic
owner datasets. Comparison normalizes JSON object field order but preserves values and array
ordering. Acceptance tests also prove that counts, follow-ups, stages, and reminders never cross
owner boundaries and that an `x-user-id` header cannot establish or replace identity.

The captured legacy sources are pinned to revision
`c9e00dd685344c2fc054212d4eb11e5d558a0f7a`:

| Source | SHA-256 |
| --- | --- |
| Analytics route | `9a030863cd6c94e9540c41aaee97627dedf02fe5829817e122d9c57c14bcddbc` |
| Application contracts | `be71f04ec783a495d9c59e1200923f790bf7b511e87abb92062eadd26bf0a56e` |
| PostgreSQL query | `802704f3ca72934a2fe23606ad4b617d3b690565f67a0e58024d054a04a9aff8` |

The fixture README records the exact source paths and the dirty-checkout caveat. All fixture
identities and application data are synthetic.

## Traffic ownership and identity gate

Legacy remains the only production traffic owner. Test traffic may reach Java only after a
trusted Spring Security principal has been established; the query derives `owner_id` exclusively
from that principal. Public request headers are not an identity bridge.

The signed identity bridge v1 is implemented and test-verified with request binding, 30-second
expiry, current/previous key rotation, shared atomic replay control, uniform 401 responses, and
owner-isolation coverage. It remains disabled by default. Routing may send a canary only to this
exact read-only path; no other analytics or write endpoint is part of the slice.

Activation still requires recorded evidence named `identity bridge verified` and
`rollback exercised`. OBSERVED additionally requires `seven-day observation complete`.

## Observation plan

For a canary, compare legacy and Java responses after the documented normalization and monitor:

- contract mismatch count, which must remain zero;
- authentication failures and cross-owner security test results;
- PostgreSQL errors, timeouts, and connection-pool saturation;
- request error rate and p95 latency, with an initial p95 budget of 500 ms;
- a minimum seven-day observation window before considering retirement of the legacy read path.

| Observation day | Java share | Mismatches | Security incidents | p95 budget breaches | Evidence |
| --- | ---: | ---: | ---: | ---: | --- |
| Not started | 0% | — | — | — | Awaiting reviewed non-public deployment |

The state remains `VERIFIED` until seven consecutive real production days are entered above. A
test run or synthetic clock advance cannot satisfy this gate.

Never log response bodies, owner identifiers, URLs, company names, or position names as comparison
evidence. Store only request IDs, implementation labels, durations, and mismatch classifications.

## Rollback

Rollback is a routing change: send all `GET /api/analytics/summary` traffic to
`legacy-jobtrace`, disable Java shadow comparison, and retain request IDs for diagnosis. Because
the Java slice is read-only and Flyway stays disabled, rollback requires no database migration,
data repair, or dual-write reconciliation.

Verification command:

```bash
./mvnw -Dtest=ContractComparisonTest,LegacySchemaSafetyTest,MigrationSliceTest,AnalyticsSummarySecurityTest test
```
