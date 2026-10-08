# Legacy-to-Java API route matrix

Source: the 65 `src/app/api/**/route.ts` files in the local legacy checkout, reviewed against Java MVC mappings on 2026-10-08. `S` = supported Java equivalent, `D` = deliberate difference, `L` = legacy-owned and intentionally absent. A pattern names one legacy route file, even when that file exports several methods.

| Legacy route file path under `/api` | State | Java equivalent / disposition |
| --- | --- | --- |
| `analytics/summary` | S | `/api/analytics/summary` |
| `analytics/report` | S | `/api/analytics/report` |
| `analytics/progress-reminders/[stageOccurrenceId]/complete` | S | same path |
| `analytics/progress-reminders/[stageOccurrenceId]/resolve` | S | same path |
| `reminders` | S | `/api/reminders` |
| `reminders/[id]` | S | same path |
| `reminders/[id]/complete` | S | same path |
| `reminders/[id]/reopen` | S | same path |
| `reminders/[id]/snooze` | S | same path |
| `reminders/[id]/retry-email` | S | same path |
| `reminder-settings` | S | same path |
| `interviews` | S | same path |
| `interviews/[id]` | S | same path |
| `interviews/public` | S | same path |
| `interviews/public/[id]` | S | same path |
| `interviews/public/[id]/like` | S | same path |
| `interviews/public/[id]/comments` | S | same path |
| `applications` | S | same path |
| `applications/[id]` | S | same path |
| `applications/[id]/detail` | S | `/api/applications/{id}/detail` dialog projection |
| `applications/[id]/status` | S | same path |
| `applications/[id]/stages` | S | same path |
| `applications/[id]/stages/[occurrenceId]` | S | same path |
| `profile` | S | same path |
| `profile/avatar` | S | same path |
| `profile/email` | S | same path |
| `profile/email/code` | S | same path |
| `profile/password` | S | same path |
| `profile/sessions` | S | same path |
| `exports/interviews` | S | same path |
| `exports/applications` | S | same path |
| `imports/preview` | S | same path |
| `imports/[id]/confirm` | S | same path |
| `internal/reminders/deliver` | S | same path; secret-protected, not a browser route |
| `admin/summary` | S | same path |
| `admin/audit-events` | S | same path |
| `admin/users` | S | same path |
| `admin/users/[id]` | S | same path |
| `admin/job-market/catalog` | S | same path, shared data read |
| `admin/job-market/sources` | S | same path, source metadata only |
| `admin/job-market/sources/[sourceId]` | S | same path, source metadata only |
| `admin/job-market/sources/[sourceId]/sync` | D | Java returns `410 ingestion_owned_externally`; legacy service executes sync |
| `admin/job-market/discovery` | S | same path, shared candidate read |
| `admin/job-market/discovery/[candidateId]` | S | same path, candidate review |
| `admin/job-market/company-candidates` | S | same path, shared candidate read |
| `admin/job-market/company-candidates/[candidateId]` | S | same path, candidate review |
| `admin/job-market/sync-runs` | S | same path, history read only |
| `admin/job-market/ats-site-scan` | L | ATS scan is legacy-owned |
| `admin/job-market/company-research` | L | research/collection is legacy-owned |
| `admin/job-market/bootstrap` | L | ingestion bootstrap is legacy-owned |
| `internal/job-market/sync` | L | legacy scheduler/collector only |
| `internal/job-market/research-companies` | L | legacy collector only |
| `internal/job-market/collect-wechat` | L | legacy collector only |
| `internal/job-market/scan-ats-boards` | L | legacy collector only |
| `job-market/campaigns` | S | same path |
| `job-market/campaigns/[campaignId]` | S | same path |
| `job-market/campaigns/[campaignId]/favorite` | S | same path |
| `auth/login` | S | same path, compatible signed session |
| `auth/logout` | S | same path |
| `auth/register` | S | same path |
| `auth/email-code` | S | same path |
| `auth/[...all]` | D | legacy Better Auth catch-all remains in legacy service; Java exposes explicit session/recovery routes only |
| `health` | S | `/api/health` |
| `health/live` | S | same path |
| `health/ready` | D | same path; Java checks database reachability but not legacy schema objects |

Java-only `/api/auth/session`, password-recovery and `/api/security/csrf` routes support the migrated browser and do not imply a legacy route-file counterpart.
