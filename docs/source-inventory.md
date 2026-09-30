# Source Repository Inventory

**Captured**: 2026-09-30  
**Source**: `/Users/songhangchi/Project/JobTrace` at the migration-foundation starting point

This inventory is a scope baseline, not evidence that an item has been migrated.

## Size

| Surface | Count |
|---|---:|
| TypeScript and TSX source lines | 32,053 |
| Domain modules | 7 |
| HTTP route handlers | 65 |
| Page routes | 21 |
| PostgreSQL migrations | 66 |
| Automated TypeScript test files | 174 |

## Domain modules

| Module | Primary responsibility | Suggested order |
|---|---|---:|
| `analytics` | Summary, reminders, and period reporting | 1, read-only pilot |
| `applications` | Private applications, stages, events, and versions | 2 |
| `interviews` | Reviews, autosave, sharing, questions, and actions | 3 |
| `reminders` | In-app reminders and email delivery coordination | 4 |
| `data-transfer` | Spreadsheet preview, confirmation, and exports | 5 |
| `job-market` | Public job sources, synchronization, favorites, and tracking | 6 |
| `identity-access` | Authentication, sessions, roles, profiles, and audit | 7, migrate last |

## HTTP surfaces

- Authentication and profile: login, logout, registration, password reset, email binding, sessions, avatar, profile.
- Private workspace: application CRUD, stages, status, details, imports, exports, analytics, and reminders.
- Interviews: private CRUD, public listing/detail, likes, comments, and exports.
- Job market: public campaigns, favorites, administrative source management, discovery, synchronization, and internal collectors.
- Administration: users, access changes, audit events, operational summary, and long-running job state.
- Operations: liveness, readiness, compatibility health, internal reminder delivery, and internal job-market scheduling.

The exact source-of-truth paths remain `src/app/api/**/route.ts`. Every migration slice must enumerate its exact handlers in its own contract document.

## Database boundary

The 66 legacy migrations own tables, custom enums, constraints, indexes, triggers, and PostgreSQL functions. Important database-enforced behavior includes:

- Owner-aware reads and writes.
- Optimistic application and interview versions.
- Atomic stage and event changes.
- Authentication rate limiting and verification attempts.
- Administrative access audit and idempotency.
- Job-market leases, fencing, lifecycle, and read-model refresh.

The new repository must not replay, rename, or edit this chain. `JOBTRACE_FLYWAY_ENABLED` stays `false` until the baseline task is approved.

## Test boundary

The source repository includes unit, component, contract, integration, end-to-end, accessibility, database, and performance coverage. Existing Playwright journeys remain the release-level behavioral oracle while backend slices move to Java.

## Inventory refresh

Refresh counts from the source repository before planning a major slice. A changed count is not itself a migration requirement; inspect the semantic change and update the relevant contract baseline.

