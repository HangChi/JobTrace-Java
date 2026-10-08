# Behavior evidence for the route matrix

The [route matrix](route-matrix.md) accounts for every pinned legacy API file. This table points to representative executable evidence for success and rejected paths; it does not claim a live production cutover or manual browser validation.

| Workflow group | Representative executable evidence | Invariants checked |
| --- | --- | --- |
| Auth and profile | `LoginIntegrationTest`, `BrowserAuthenticationIntegrationTest`, `AccountSessionRevocationIntegrationTest`, `PasswordRecoveryHttpIntegrationTest` | Signed cookie and bridge identity, current session, ordinary-user admin denial, origin/CSRF, recovery and revocation |
| Applications and stages | `ApplicationCommandIntegrationTest`, `ApplicationStageCommandIntegrationTest`, `ApplicationReadContractTest`, `ApplicationOwnerIsolationIntegrationTest` | Create-to-read parity, version conflict, one concurrent winner, private owner isolation |
| Interviews | `InterviewCommandIntegrationTest`, `PublicInterviewReadIntegrationTest`, `PublicInterviewEngagementTest`, `InterviewReadSecurityTest` | Private command ownership, public sanitization and engagement |
| Reminders and analytics | `ReminderLifecycleIntegrationTest`, `ReminderDeliveryIntegrationTest`, `AnalyticsReportIntegrationTest`, `ProgressReminderResolutionTest` | Lifecycle, once-only delivery/resolution, owner report data |
| Data transfer | `ImportRoundTripIntegrationTest`, `ImportConfirmIntegrationTest`, `ExportSecurityTest` | Preview before confirmation, duplicate handling, private exports |
| Administration | `AdminReadQueryIntegrationTest`, `AdminAccessCommandIntegrationTest` | Role gate, audit and atomic access changes |
| Marketplace | `JobMarketCandidateIntegrationTest`, `JobMarketSourceAdminIntegrationTest`, `JobMarketFavoriteIntegrationTest`, `JobMarketSyncAdminIntegrationTest` | Shared-data reads/curation, favorites and legacy-owned sync trigger |
| Health and shared errors | `HealthControllerIntegrationTest`, `GlobalErrorHttpContractTest`, `GlobalExceptionHandlerTest` | Legacy health alias, no-store and safe correlated failures |
| Schema and route ownership | `LegacySchemaSafetyTest`, `MigrationActivationGateTest`, `RouteParityInventoryTest` | No Flyway history, no Java marketplace collector, all 65 route dispositions |

Intentional differences are limited to the matrix's `D` rows: the legacy Better Auth catch-all is replaced by explicit Java auth routes; Java's manual source-sync path responds `410` and never ingests; Java readiness checks database reachability rather than the legacy route's three schema objects. The restored `/api/health` route returns the old `{status}` shape and performs a database check.

Contract fixtures and integration tests from stages 001–027 remain the primary detailed behavior checks. Stage 029 adds the route-level guard, health parity, malformed-request contract, owner read-after-write, and concurrent conflict regressions rather than duplicating every prior fixture.
