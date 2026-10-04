# Research: Account Profile Update

## Established request and response

**Decision**: Preserve `PATCH /api/profile` with required `displayName`, optional `image`, and result fields `id`, `displayName`, `image`, `recoveryEmail`, and `emailVerified`.

**Rationale**: The current profile form consumes this response directly. Display name is trimmed and limited to 100 characters; an omitted, empty, or null image clears the avatar. Additional properties are ignored by the established object parser.

**Alternatives considered**: A new endpoint or reduced result would require frontend changes.

## Persistence boundary

**Decision**: Use one parameterized update constrained by the verified owner and return recovery-email fields from the same statement.

**Rationale**: The existing identity mapping writes `display_name`, `image`, and `updated_at`. Returning read-only recovery fields avoids a second query and detects missing owners without exposing foreign rows.

**Alternatives considered**: A new network call to the legacy identity runtime adds deployment coupling; select-before-update adds a race.

## Avatar handling

**Decision**: Accept an absolute URI with a scheme while treating empty, null, and omission as null.

**Rationale**: File upload, signature inspection, size limits, and object storage remain with the existing upload route.

**Alternatives considered**: Migrating upload would mix multipart/cloud-storage concerns into a profile-row command.

## Security and observability

**Decision**: Protect only exact PATCH, apply no-store, and emit bounded outcomes without profile values.

**Rationale**: Exact method/path/request binding prevents cross-command assertion reuse and sensitive metric cardinality.

**Alternatives considered**: Protecting all profile methods would widen authority to excluded operations.
