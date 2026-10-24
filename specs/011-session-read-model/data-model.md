# Data Model: Account Session Read Model

## AccountSession

An unexpired login session owned by the authenticated account.

| Field | Type | Rules |
|---|---|---|
| `id` | string | Opaque, non-blank, at most 255 characters |
| `createdAt` | UTC date-time string | Required, millisecond precision |
| `expiresAt` | UTC date-time string | Required and later than the database read instant |
| `userAgent` | nullable string | Preserved without parsing or logging |
| `current` | boolean | True only when `id` equals the supplied current-session reference |

## CurrentSessionReference

Trusted caller metadata used only for the current marker.

| Field | Type | Rules |
|---|---|---|
| `sessionId` | string | Required, non-blank, 1-255 characters; never logged |

## Relationships and invariants

- Every returned session belongs to exactly the account identified by the verified bridge principal.
- Foreign and expired rows never enter the returned model.
- Zero or one returned sessions are current because session identifiers are unique.
- An unmatched current-session reference produces no current item and reveals no foreign-session existence.
- The model has no state transition in this slice; all session lifecycle changes remain external.
