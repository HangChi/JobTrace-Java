# Feature Specification: Frontend Migration

**Feature Branch**: `codex/028-frontend`

**Created**: 2026-10-08

**Status**: Complete — validated on 2026-10-26; see [validation report](validation-report.md)

**Input**: Replace the Java build's health-check-only shell with the existing JobTrace browser workflows for login, applications, interviews, reminders, analytics, profile, import and administration, using Java APIs.

## User Scenarios & Testing

### User Story 1 - Enter and navigate the workspace (Priority: P1)

A job seeker signs in, stays signed in on refresh, navigates to a deep-linked workspace page and signs out. Unauthenticated visitors can register and recover access.

**Independent Test**: Load the login page without a session, sign in with a mocked Java response, refresh a protected deep link and sign out; verify failures and keyboard navigation.

**Acceptance Scenarios**:

1. **Given** no valid session, **when** a protected page opens, **then** the user is sent to login with a safe return path.
2. **Given** valid credentials, **when** login succeeds, **then** the Java session cookie is used and the user reaches the intended page.
3. **Given** invalid credentials or an unavailable service, **when** login fails, **then** the form retains the identifier, shows a specific recovery action and does not claim success.
4. **Given** an authenticated user, **when** sign-out succeeds, **then** protected content is cleared and the login page appears.

### User Story 2 - Track applications and reminders (Priority: P2)

A job seeker can browse, filter, create, edit and delete applications, manage stage history and reminders, inspect analytics and import previously exported data.

**Independent Test**: Create an application, update a stage, complete a reminder, inspect the updated analytics and preview/confirm a CSV import using mocked Java APIs.

**Acceptance Scenarios**:

1. **Given** an application list, **when** search and filters change, **then** the result, count and URL stay in sync and back navigation restores context.
2. **Given** a valid edit, **when** it is saved, **then** the list/detail state updates and success is announced; validation and network errors preserve entered data.
3. **Given** an import preview with invalid rows, **when** the user confirms selected valid rows, **then** row outcomes are shown without silently importing invalid rows.

### User Story 3 - Share interview learning and manage a profile (Priority: P3)

A job seeker can manage private interviews, browse public interviews with engagement, and update profile, credentials and account preferences.

**Independent Test**: Create a private interview, browse and comment on a public interview, update profile and inspect success/error states through mocked Java APIs.

**Acceptance Scenarios**:

1. **Given** a private interview, **when** it is edited or deleted, **then** only the current user's item changes.
2. **Given** a public interview, **when** liking or commenting, **then** the new state is visible and a failed request can be retried.
3. **Given** profile settings, **when** a valid change is saved, **then** the current account view reflects it without a full reload.

### User Story 4 - Administer the workspace (Priority: P4)

An administrator can reach the current Java administration pages, inspect users and audit history, make supported account changes, and browse shared marketplace data. Ingestion remains owned by the legacy service.

**Independent Test**: Enter as admin and ordinary user; verify role-gated navigation, summary/user/audit reads and account actions, including error and empty states.

**Acceptance Scenarios**:

1. **Given** an administrator session, **when** admin pages open, **then** live Java data is shown with loading, empty and failure states.
2. **Given** an ordinary user, **when** an admin URL is opened directly, **then** access is denied and no admin data is rendered.
3. **Given** the marketplace ingestion controls, **when** viewed in Java, **then** shared catalog and run data are readable while crawl execution is clearly identified as legacy-owned and is not invoked from Java.

### Edge Cases

- Expired sessions during a mutation return to login without treating the mutation as successful.
- All request paths are same-origin; frontend code never sends trusted identity headers or stores session tokens in browser storage.
- Unsafe `returnTo` values cannot navigate off-site.
- Large lists, empty states, slow responses and offline failures remain understandable and actionable.
- Forms retain entered values after validation failures and prevent accidental duplicate submission.
- The interface works by keyboard, with visible focus, semantic controls, text alternatives and sufficient contrast at supported widths.

## Requirements

### Functional Requirements

- **FR-001**: Replace the placeholder with route-addressable login, account recovery and protected workspace pages connected to Java APIs.
- **FR-002**: Use the Java browser session cookie and current-session endpoint for auth state; never forge owner/admin headers.
- **FR-003**: Provide application list/detail/create/edit/status/stage and reminder workflows equivalent to supported old-version behavior.
- **FR-004**: Provide analytics summary/report, export and import preview/confirm workflows.
- **FR-005**: Provide private/public interview workflows and profile/account settings supported by Java APIs.
- **FR-006**: Provide role-gated administration for Java-supported summary, users, audit and shared marketplace reads; identify legacy-owned crawling as outside the Java frontend.
- **FR-007**: Preserve familiar JobTrace terminology and visual identity, with responsive, accessible loading/empty/success/validation/failure states.
- **FR-008**: Add automated route, request-contract, form and accessibility tests; keep build artifacts embedded in the Spring Boot application.
- **FR-009**: Do not change CI/CD, production routing or the old Next.js frontend in this stage.

### Key Entities

- **Browser session**: Current user, role, expiry and signed cookie managed by Java.
- **Application**: Job application, status, stage history and related reminder state.
- **Interview**: Private or public record and public engagement.
- **Import batch**: Private preview and confirmation outcomes.
- **Admin view**: Summary, managed users and audit records available by role.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All listed supported flows are reachable by direct URL and keyboard, with no dead-end placeholder screen.
- **SC-002**: Automated tests cover success, validation, unauthorized, empty and dependency-failure states for every migrated workflow.
- **SC-003**: No tested unauthenticated or ordinary-user path renders protected or administrator data.
- **SC-004**: At 375, 768, 1024 and 1440 CSS pixels, pages avoid horizontal scrolling and keep primary actions operable.
- **SC-005**: Representative lab measurements meet LCP <=2.5 s p75, INP <=200 ms p75 and CLS <=0.1; Java reads and writes retain existing latency budgets.
- **SC-006**: Changed frontend production code maintains at least 80% line and branch coverage.

## Assumptions

- Java APIs through completed stage 027 are the source of truth for this branch. Marketplace data is read from the shared remote database; Java does not run a separate crawler or silently proxy requests to Next.js.
- The old frontend is a reference for workflow, labels and visual identity, but this work edits only `JobTrace-Java/frontend/` and migration documentation.
- No new router, component or chart dependency is required unless implementation evidence justifies it.
