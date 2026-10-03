# Feature Specification: Job Market Read Model Migration

**Feature Branch**: `codex/008-job-market-read-model`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "开始008". Under the established migration roadmap, feature 008 begins the job-market domain with a bounded private read-model slice. Existing synchronization, administration, favorites, tracking writes, sessions, schema ownership, deployment, and production traffic remain with the current service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse Current Recruitment Campaigns (Priority: P1)

As an authenticated user, I can browse a paged list of current recruitment campaigns so that I can quickly see which companies, positions, locations, recruitment channels, and application options are available without losing my private favorite state.

**Why this priority**: Browsing current opportunities is the primary value of the recruitment marketplace and provides a useful independently testable migration slice without moving any writer or synchronization responsibility.

**Independent Test**: Seed current, stale, closed, favorite, and foreign-owner personalization data; request the default first page as two different users; verify established fields, default ordering, pagination metadata, closed-record rules, and complete isolation of favorites.

**Acceptance Scenarios**:

1. **Given** approved current recruitment data, **When** an authenticated user opens the marketplace without filters, **Then** they receive the first page using the established default size and ordering, excluding closed opportunities.
2. **Given** two users with different favorites for the same shared recruitment data, **When** each browses the marketplace, **Then** shared campaign content is identical while each user sees only their own favorite state.
3. **Given** no eligible recruitment data, **When** the user opens the marketplace, **Then** they receive an empty page with correct pagination metadata rather than an error.

---

### User Story 2 - Find Relevant Opportunities (Priority: P2)

As an authenticated user, I can search, filter, and page through recruitment campaigns so that I can narrow the marketplace by keyword, company, location, status, posting date, and favorites.

**Why this priority**: The marketplace becomes difficult to use as its catalog grows unless users can reproduce the current discovery and pagination behavior.

**Independent Test**: Use a deterministic mixed catalog and verify each filter independently and in combination, including exact totals, stable ordering, page boundaries, input bounds, favorite-only behavior, and inclusive posting dates.

**Acceptance Scenarios**:

1. **Given** campaigns across multiple companies, positions, locations, statuses, and dates, **When** the user combines supported filters, **Then** every returned company matches all supplied criteria and the total reflects the full filtered result set.
2. **Given** more results than fit on one page, **When** the user moves between pages, **Then** results follow the established stable order without duplicates within the unchanged data set.
3. **Given** the favorite-only filter, **When** the user requests results, **Then** closed companies may appear only when they are eligible under the established favorite behavior, and no other user's favorites affect the result.
4. **Given** malformed or out-of-range filter or pagination input, **When** the user submits it, **Then** the request is rejected with safe validation feedback and no private data is disclosed.

---

### User Story 3 - Inspect Campaign Jobs (Priority: P3)

As an authenticated user, I can inspect one recruitment campaign and its current jobs so that I can choose a valid application target and recognize jobs I already track.

**Why this priority**: Detail completes the read journey from discovery to an informed application decision while preserving the current service as the sole owner of tracking and application writes.

**Independent Test**: Request representative synced and directory-backed campaign details for two users; verify campaign metadata, ordered jobs, locations, source attribution, application availability, status/date handling, private tracked-application markers, and safe not-found behavior.

**Acceptance Scenarios**:

1. **Given** an eligible campaign with current jobs, **When** the user opens its detail, **Then** they receive the established campaign summary plus the complete ordered list of eligible jobs and application availability.
2. **Given** the same job is tracked by one user but not another, **When** both users view the detail, **Then** only the owning user sees their tracked-application reference.
3. **Given** a missing, invalid, or ineligible campaign identifier, **When** the user requests detail, **Then** they receive safe validation or not-found feedback without learning private user state.
4. **Given** a job cannot currently be applied to, **When** detail is shown, **Then** its unavailable state and established safe reason are returned instead of an unsafe or misleading link.

### Edge Cases

- The requested page is beyond the final page, or the filtered result set becomes empty.
- Search, company, or location input contains surrounding whitespace, mixed case, multilingual text, or reaches the established length boundary.
- A posting date equals the inclusive lower-bound date.
- Published, validity, or last-confirmed timestamps are absent.
- A company has more positions than the list preview displays, while detail must remain complete.
- A campaign is closed but is requested through the closed-status or favorite-only behavior.
- A campaign contains stale or closed jobs, multiple source records, duplicate locations, a missing application link, or a non-web source/application link.
- Shared market data exists while the requesting user has no favorite or tracking rows.
- Another user's favorite or tracked-application rows exist for the same company or job.
- Authentication, personalization storage, or shared marketplace storage is unavailable.
- Recruitment data changes while a user is paging; each response must remain internally consistent and deterministically ordered for the data visible to that request.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST require a trusted authenticated user for both marketplace list and campaign detail reads; caller-supplied identity values MUST NOT select an owner.
- **FR-002**: The system MUST expose only read behavior in this slice and MUST NOT create or change favorites, tracked applications, campaigns, companies, jobs, sources, synchronization runs, or administrative state.
- **FR-003**: The default marketplace view MUST return page 1 with 20 companies, accept page sizes from 1 through 100, and reject invalid pagination values.
- **FR-004**: The marketplace list MUST preserve the established stable order: newest publication first, then most recently confirmed, then company identity as the final tie-breaker; missing timestamps sort after present timestamps.
- **FR-005**: The default list MUST exclude closed opportunities. An explicit closed-status request or the established favorite-only behavior MUST control when closed data is eligible.
- **FR-006**: Users MUST be able to filter by keyword, company, location, status, inclusive posting-date lower bound, and favorite state, with combined filters applying conjunctively.
- **FR-007**: Search, company, and location inputs MUST be trimmed, case-insensitive where currently established, and limited to 100 characters.
- **FR-008**: List responses MUST preserve the established campaign summary information: listing kind, company identity/name/type/industry, recruitment type, positions and position count, locations, status, application mode and target, source attribution, publication/validity/confirmation times, and the requesting user's favorite state.
- **FR-009**: List responses MUST include the normalized page, page size, and total matching company count, including correct metadata for empty and out-of-range pages.
- **FR-010**: List previews MUST preserve the current maximum preview of 50 position names without changing the complete position count.
- **FR-011**: Campaign detail MUST preserve all summary information and add the established ordered eligible jobs, including job identity, title, locations, status, application target or unavailable reason, publication/validity times, source attribution, and the requesting user's tracked-application reference.
- **FR-012**: Detail MUST exclude jobs that are not eligible under the established current-service rules and MUST preserve the established job ordering.
- **FR-013**: Favorite state and tracked-application references MUST be scoped strictly to the authenticated user. Shared campaign content MUST NOT expose whether another user favorited or tracked it.
- **FR-014**: Missing eligible campaign detail MUST use the established not-found behavior; malformed input MUST use safe validation behavior; storage failures MUST fail closed with a request identifier and no private content.
- **FR-015**: Returned application and source targets MUST preserve only established safe destinations; missing or unsafe targets MUST be represented as unavailable rather than exposed as actionable links.
- **FR-016**: Private personalized responses MUST not be stored by shared or public caches and MUST not mix one user's personalization into another user's response.
- **FR-017**: Diagnostics MUST use bounded operation, outcome, and latency categories and MUST NOT contain user identifiers, campaign identifiers, filter text, URLs, favorite state, tracked-application identifiers, or returned content as labels or log values.
- **FR-018**: The current service MUST remain the sole synchronization runner, marketplace writer, favorite/tracking writer, administrator, session authority, schema owner, and production traffic owner for this slice.
- **FR-019**: The existing browser experience, routes, terminology, empty states, keyboard behavior, and responsive presentation MUST remain unchanged by this backend migration slice.
- **FR-020**: The list and detail operations MUST each meet a 500 millisecond p95 response budget under representative catalog and personalization load, with work bounded independently of unrelated catalog size.

### Key Entities

- **Campaign Summary**: A company-level recruitment listing that combines the representative campaign, company metadata, position preview/count, locations, status, safe application choice, source attribution, lifecycle times, and the current user's favorite state.
- **Campaign Detail**: A campaign summary plus the complete ordered set of eligible current jobs and user-specific tracking markers.
- **Campaign Job**: One eligible role with title, locations, lifecycle status/times, source, safe application availability, and an optional tracked-application reference owned by the requesting user.
- **Marketplace Query**: Normalized search, company, location, status, date, favorite, and pagination criteria; it never contains or selects an owner.
- **Personalization State**: Favorite and tracked-application relationships owned by one authenticated user and projected into otherwise shared marketplace data.
- **Company Read Model**: The current company-level marketplace projection maintained by the existing synchronization system and consumed without taking writer ownership.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For representative default, empty, filtered, favorite-only, closed, and out-of-range pages, 100% of asserted fields, totals, ordering, and eligibility decisions match the current service.
- **SC-002**: For representative synced and directory-backed details, 100% of asserted campaign fields, job fields, job ordering, application availability, and source attribution match the current service.
- **SC-003**: In a two-user data set, zero favorite or tracked-application values belonging to one user appear in the other user's list or detail across all tested scenarios.
- **SC-004**: Users receive clear validation, not-found, authentication, and temporary-failure feedback for 100% of tested invalid and unavailable cases, with no sensitive content in responses or diagnostics.
- **SC-005**: After 10 warm-up samples, at least 95% of 40 representative list requests and 40 representative detail requests complete within 500 milliseconds, including response serialization.
- **SC-006**: Existing marketplace screens require no user workflow, navigation, copy, accessibility, or responsive-layout change to consume the migrated read behavior.
- **SC-007**: Verification shows zero marketplace, favorite, tracking, synchronization, administrative, or schema records created, updated, or deleted by the new read slice.

## Assumptions

- Feature 008 is the first bounded job-market slice and migrates only authenticated marketplace list and campaign-detail reads.
- The existing service continues collecting, normalizing, approving, synchronizing, closing, and administering marketplace data and remains the only writer.
- Favorite mutations, tracking/application creation, public or administrative marketplace operations, source discovery, collectors, scheduled synchronization, and manual synchronization are deferred to separately reviewed slices.
- Existing signed identity bridging is reused conceptually; production issuer/routing activation requires a separate release decision and is not part of feature completion.
- Existing marketplace storage and company-level read projections remain unchanged; this feature adds no production schema migration.
- No Java deployment or production traffic switch is planned for feature 008. Completion means specification parity, automated verification, and review readiness only.
