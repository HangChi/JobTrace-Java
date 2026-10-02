# Feature Specification: Private Data Export Migration

**Feature Branch**: `codex/007-private-data-export`

**Created**: 2026-10-02

**Status**: Draft

**Input**: User description: "合并一下，然后开始007". After the merged reminder read slice, the migration roadmap's next domain is data transfer. This first bounded slice covers private application and interview exports only; imports and all writes remain with the existing service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Download My Application Records (Priority: P1)

As a job seeker, I can download my application records in a spreadsheet-compatible format, including their stage history, so I can retain a personal copy or analyze them outside JobTrace.

**Why this priority**: Application export is the core data portability path. It offers immediate value without transferring import or business-write ownership.

**Independent Test**: With distinct records for two users, export all records and a filtered subset for one user; verify the downloaded fields, order, format, and stage history against the current service, with zero other-user data or source mutations.

**Acceptance Scenarios**:

1. **Given** several owned applications, **When** the user chooses all records, **Then** the download contains all owned records in the established order and no other user's records.
2. **Given** search, status, type, stage, city, and date filters, **When** the user exports the filtered view, **Then** the download contains exactly the matching owned records with their full ordered stage histories.
3. **Given** records with commas, quotes, newlines, Chinese text, links, and spreadsheet-formula-like text, **When** the user downloads either supported format, **Then** the values remain readable and potentially active formulas are rendered as inert text.

---

### User Story 2 - Download Selected Applications (Priority: P2)

As a job seeker, I can export only the applications I selected, so a shared or archived file does not include unrelated applications.

**Why this priority**: Selection is an established privacy-sensitive export mode distinct from all and filtered exports.

**Independent Test**: Select a mixture of owned and another user's identifiers; verify only owned selected records appear, filters do not silently broaden selection, and an empty or invalid selection fails safely.

**Acceptance Scenarios**:

1. **Given** one or more selected owned applications, **When** the user downloads the selection, **Then** only those records are present.
2. **Given** selected identifiers that include another user's record, **When** the download is prepared, **Then** no foreign record or identifying field appears.
3. **Given** no selection or more than the established 100 selected identifiers, **When** the user requests selected export, **Then** the request is rejected without producing a partial file.

---

### User Story 3 - Download My Interview Reviews (Priority: P3)

As a job seeker, I can download one interview review as a readable document or several as a single archive, so I can keep my private reflections outside the application.

**Why this priority**: Interview export complements the previously migrated private interview reads while preserving the established download behavior.

**Independent Test**: With reviews belonging to two users, export one and several owned reviews; compare document text, names, selection order, archive contents, and privacy behavior against the existing service.

**Acceptance Scenarios**:

1. **Given** one owned review, **When** the user downloads it, **Then** one readable document is returned with the established content and a safe descriptive filename.
2. **Given** multiple selected owned reviews, **When** the user downloads them, **Then** one archive contains one document per review in selected order, with unique safe names even when preferred names collide.
3. **Given** missing, duplicate, invalid, or foreign review identifiers, **When** the user requests export, **Then** invalid selection is rejected, duplicates do not duplicate files, and foreign content is never disclosed.

### Edge Cases

- There are no owned records in the chosen application scope, or no owned reviews among selected identifiers; the user receives the established safe not-found feedback rather than an ambiguous empty file.
- A selected application or interview is deleted between selection and download; only still-owned records may appear, and no other record may replace it.
- A linked interview stage is removed; the exported review uses the same stored-stage fallback as the existing service.
- Text contains filename path separators, control characters, repeated preferred names, non-ASCII characters, or spreadsheet formula prefixes; downloads remain safe and usable.
- A review has no questions, one plain-text question, multiple questions, reflections, or action items; document content follows the current export representation without adding private fields.
- Storage or trusted identity validation is unavailable; the request fails safely, without using a caller-provided owner or exposing source data in an error or diagnostic.
- Large all/filtered application exports must preserve the current inclusion contract; planning must measure resource usage and avoid an unannounced truncation.
- The existing browser's loading, empty, download-success, and error feedback remains unchanged; this slice does not redesign the interface.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every export MUST use the reviewed trusted identity and MUST reject public owner headers or other caller-controlled ownership claims.
- **FR-002**: Application, stage-history, review, linked-application, question, and action-item data MUST be restricted to the authenticated owner throughout the export.
- **FR-003**: Application export MUST preserve the established all, filtered, and selected scopes. An omitted or unrecognized scope MUST use the current default.
- **FR-004**: Application export MUST offer the established CSV and XLSX formats, using the current default when format is omitted or unrecognized.
- **FR-005**: Filtered application export MUST apply the current search, status, type, stage, city, and inclusive application-date criteria; all and selected scopes MUST not silently inherit filtered criteria.
- **FR-006**: Selected application export MUST require at least one valid identifier and accept no more than 100 provided identifiers; it MUST never include an unselected application.
- **FR-007**: Application export MUST preserve the current column order and values for identifier, company, position, city, link, application date, type, status, latest date, ordered stage history, notes, and creation/update times.
- **FR-008**: Application rows MUST retain the established newest-application-date and stable identifier order; stage history MUST retain its established chronological and stable tie-break order.
- **FR-009**: CSV and XLSX output MUST preserve multilingual text and delimiters while rendering formula-like user text inert; links MUST remain safe to open only when they use an accepted web scheme.
- **FR-010**: Interview export MUST accept one to 100 selected valid review identifiers, ignore duplicate selections, and preserve the established order of distinct identifiers.
- **FR-011**: One selected owned review MUST download as the established readable Markdown document; multiple owned reviews MUST download as a ZIP archive with one document per review.
- **FR-012**: Interview document text and filename components MUST preserve the existing review-export contract, including stored-stage fallback, plain-question behavior, safe character replacement, and collision suffixes.
- **FR-013**: If the chosen application scope or review selection contains no owned result, export MUST return safe not-found feedback rather than an empty file. Invalid selection MUST return safe validation feedback.
- **FR-014**: Downloads MUST carry the correct file type and attachment filename, and MUST not be cached in a way that can expose another user's private data.
- **FR-015**: Representative binary and text outputs MUST be compared with current-service fixtures for content, field/entry order, filenames, media type, and security behavior.
- **FR-016**: Errors and logs MUST use the project's safe request identifier and MUST not expose export content, personal data, file contents, trusted assertions, or storage details.
- **FR-017**: Diagnostics MUST use bounded operation/outcome and latency categories, never user identifiers, selected IDs, filenames, search text, or exported content as labels.
- **FR-018**: This feature MUST be read-only. The existing service remains the sole owner of import preview, import confirmation, import batches, application and interview writes, browser sessions, schema changes, and production traffic.
- **FR-019**: The slice MUST NOT initiate deployment, production routing, or a change to the existing browser workflow.

### Key Entities *(include if feature involves data)*

- **Application Export Selection**: The user's choice of all, filtered, or selected owned application records and output format.
- **Application Export Row**: One owned application plus ordered stage history, expressed in the established portable columns.
- **Interview Export Selection**: The ordered set of distinct review identifiers supplied by the user, bounded to the established maximum.
- **Interview Export Document**: A readable representation of one owned review with a safe descriptive filename.
- **Export Download**: A private file response containing one spreadsheet, one document, or an archive of documents, together with type and attachment metadata.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For representative all, filtered, and selected application exports, 100% of asserted row values, columns, stage-history items, and ordering match the current service.
- **SC-002**: For representative single and multiple interview exports, 100% of asserted document text, filenames, archive entries, and entry order match the current service.
- **SC-003**: Two-owner isolation tests disclose zero foreign application, stage, review, question, or action-item values across both export types.
- **SC-004**: In a representative set of up to 100 selected records, 95% of downloads complete within 500 milliseconds, including file generation.
- **SC-005**: All tested invalid, empty, duplicate, unsafe-text, missing-link, and unavailable-dependency cases produce the specified safe outcome without creating or changing business data.
- **SC-006**: A maintainer can validate the slice from a clean checkout using synthetic data without importing a file, sending an email, changing production traffic, or deploying Java.

## Assumptions

- “开始007” follows the Java migration roadmap's data-transfer domain. As with features 004–006, its first bounded implementation is read-only; application and interview exports are in scope, while import preview/confirmation and writer ownership require a later separately reviewed slice.
- The current service's runtime output is the compatibility oracle. The existing application export has all/filtered/selected scopes, CSV/XLSX formats, and a 100-ID selected maximum; interview export has a 1–100-ID selection and single-document/multi-document behavior. Planning will verify exact defaults, headers, filenames, encoding, and failure status before implementation.
- The existing signed identity bridge remains opt-in. The current service retains all production traffic; this feature does not deploy Java.
- The existing browser UI and accessibility behavior remain in place. Any future interface redesign or route switch requires separate specification and authorization.
