# Feature Specification: Signed Identity Bridge and Analytics Activation

**Feature Branch**: `codex/003-signed-identity-bridge`

**Created**: 2026-10-01

**Status**: Draft

**Input**: User description: "Start feature 003 for the signed identity bridge and production activation of the verified analytics-summary migration slice."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Seamless authenticated analytics access (Priority: P1)

An authenticated JobTrace user can open analytics through the existing application and receive only
their own summary while the existing service continues to own login and session validation.

**Why this priority**: The verified Java analytics slice cannot receive production traffic until it
has a trustworthy identity that preserves the current user experience and owner isolation.

**Independent Test**: Authenticate through the existing application, request the analytics summary
through the internal routing path, and confirm the response matches the legacy result for the same
user without requiring another login or exposing another owner's data.

**Acceptance Scenarios**:

1. **Given** a user with a valid legacy session, **When** the existing service forwards an analytics
   request with an approved identity assertion, **Then** the Java service returns that user's
   owner-isolated analytics summary.
2. **Given** two users with different application data, **When** each requests analytics, **Then**
   each response contains only data belonging to the authenticated user.
3. **Given** no valid identity assertion, **When** analytics is requested, **Then** access is denied
   without querying or disclosing owner data.

---

### User Story 2 - Reject forged, stale, or replayed identity (Priority: P2)

A security operator can rely on the bridge to reject identity assertions that are forged, expired,
used for another request, signed by an unknown key, or replayed after successful use.

**Why this priority**: A bridge that accepts attacker-controlled identity would break the primary
security boundary of every later protected migration slice.

**Independent Test**: Submit valid and deliberately invalid assertions covering signature, issuer,
audience, lifetime, request binding, user identity, role, access version, key selection, and replay;
confirm only the valid first use authenticates.

**Acceptance Scenarios**:

1. **Given** an altered identity assertion, **When** it is presented, **Then** access is denied and
   the asserted user is never authenticated.
2. **Given** a valid assertion for a different method, path, or request identifier, **When** it is
   presented to the analytics endpoint, **Then** access is denied.
3. **Given** a valid assertion that has already succeeded once, **When** it is presented again,
   **Then** the replay is denied.
4. **Given** overlapping current and previous signing keys during rotation, **When** assertions use
   either approved key, **Then** both validate until the previous key is retired; unknown keys fail.

---

### User Story 3 - Controlled analytics canary and rollback (Priority: P3)

The sole maintainer can route a bounded share of analytics reads to Java, compare outcomes without
recording personal data, observe the slice for seven days, and return all traffic to the legacy
implementation quickly if any release condition fails.

**Why this priority**: Authentication readiness alone does not justify activation; production
ownership must move through a measured, reversible release process.

**Independent Test**: Exercise the canary runbook with synthetic traffic, verify sanitized metrics
and contract comparisons, trigger the rollback procedure, and confirm legacy resumes serving all
analytics reads without a schema or data repair.

**Acceptance Scenarios**:

1. **Given** all preflight gates pass, **When** a canary is enabled, **Then** only
   `GET /api/analytics/summary` is eligible for Java routing and legacy remains the sole writer.
2. **Given** a mismatch, authentication anomaly, elevated error rate, or latency budget breach,
   **When** the rollback threshold is reached, **Then** all analytics traffic returns to legacy.
3. **Given** seven consecutive observation days with zero contract mismatches and all security and
   performance gates passing, **When** the maintainer records approval, **Then** the slice may be
   marked production-active.

### Edge Cases

- The assertion is syntactically invalid, incomplete, excessively large, or contains duplicate
  identity fields.
- Clock skew places an assertion just before its valid-from time or just after its expiry.
- The key identifier is missing, unknown, retired, or maps to malformed key material.
- The request identifier is absent, regenerated, or differs between the signed request and the
  request received by Java.
- Two requests race to use the same one-time assertion.
- A public client supplies legacy identity headers without a valid signed assertion.
- The replay-control store is unavailable or saturated.
- A canary comparison cannot reach one implementation or returns an unclassifiable difference.
- A restart occurs during the observation window or key-rotation overlap.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The existing JobTrace service MUST remain the identity and session authority for this
  feature.
- **FR-002**: The bridge MUST authenticate only short-lived identity assertions created after the
  existing service has validated the user's session.
- **FR-003**: Every assertion MUST identify the issuer, intended audience, user, role, access
  version, request identifier, signing key, validity window, and unique one-time identifier.
- **FR-004**: Every assertion MUST be bound to the HTTP method and normalized request path for which
  it was issued.
- **FR-005**: Assertions MUST expire no more than 30 seconds after issuance, with at most 5 seconds
  of documented clock-skew tolerance.
- **FR-006**: The bridge MUST reject missing, malformed, oversized, incorrectly signed, premature,
  expired, misaddressed, request-mismatched, unknown-key, and replayed assertions.
- **FR-007**: Assertion comparison and validation MUST avoid timing-sensitive secret comparison and
  MUST fail closed when validation or replay protection is unavailable.
- **FR-008**: The bridge MUST support an explicitly identified current signing key and at least one
  previous key during a bounded rotation overlap, without placing key material in source control.
- **FR-009**: Public identity headers, cookies, query parameters, and request bodies MUST NOT
  establish or replace the authenticated identity in Java.
- **FR-010**: A successfully validated assertion MUST establish one authenticated principal whose
  name is the asserted user identifier and whose authorities are derived only from the validated
  role and access version.
- **FR-011**: Security diagnostics MUST record only safe classifications, request identifiers, key
  identifiers, and timing information; they MUST NOT record signatures, key material, session
  values, user identifiers, or analytics response bodies.
- **FR-012**: The bridge MUST initially authorize only `GET /api/analytics/summary`; no write route or
  other protected capability is included.
- **FR-013**: Canary comparison MUST preserve values and array ordering while ignoring only JSON
  object field order, and MUST record zero personal or response-body data.
- **FR-014**: Production activation MUST require successful negative security tests, contract
  parity, owner-isolation tests, the read latency budget, an exercised routing rollback, and a
  written Solo-Maintainer Mode self-review.
- **FR-015**: The analytics slice MUST complete seven consecutive observation days with zero
  contract mismatches before it can be marked production-active.
- **FR-016**: Rollback MUST require only a routing/configuration change and MUST NOT require a
  database migration, data repair, or dual-write reconciliation.

### Key Entities

- **Identity Assertion**: A short-lived, signed, request-bound statement of the already authenticated
  user's identity and authorization context.
- **Signing Key Descriptor**: The non-secret identifier, lifecycle state, and validity boundaries
  used to select externally supplied signing material during rotation.
- **Replay Record**: A temporary record that a unique assertion has already been accepted, retained
  only long enough to prevent reuse during its validity window.
- **Canary Observation**: A sanitized outcome for one routed request, including implementation,
  duration, status class, and mismatch classification without user or response content.
- **Activation Decision**: The maintainer's recorded evidence that all security, parity,
  performance, observation, and rollback gates passed.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All negative identity cases are denied, and 100% of accepted test requests resolve to
  the expected owner with no cross-owner data exposure.
- **SC-002**: Valid users continue to access analytics without an additional login or visible change
  in the workflow.
- **SC-003**: Representative analytics reads complete within 500 milliseconds at the 95th
  percentile, including identity validation overhead.
- **SC-004**: The canary records zero normalized response mismatches and zero confirmed identity or
  owner-isolation incidents for seven consecutive days.
- **SC-005**: An operator can restore 100% legacy routing within five minutes using the documented
  rollback procedure.
- **SC-006**: Current-to-previous key rotation completes without rejecting valid requests during the
  documented overlap, and retired or unknown keys achieve a 100% rejection rate.
- **SC-007**: Automated verification maintains at least 80% line and branch coverage for changed
  production behavior and passes every required repository quality gate.

## Assumptions

- The legacy service can issue an assertion only after validating its existing Better Auth session.
- Production ingress can restrict the bridge path to traffic from the legacy service; the
  cryptographic assertion remains mandatory even on that path.
- Replay protection is shared by all Java instances that may accept bridge traffic; a local-only
  replay cache is not sufficient for a multi-instance production deployment.
- The analytics slice remains read-only and uses the existing PostgreSQL schema with Flyway disabled.
- Deployment routing and secret distribution are environment responsibilities; this repository
  defines their required contract, validation, evidence, and rollback behavior.
- Authentication for capabilities other than analytics summary and final migration of credentials,
  sessions, password reset, and account lifecycle remain out of scope.
