# Data Model: Account Profile Update

## Profile Update Command

| Field | Type | Rule |
|---|---|---|
| ownerId | string | Trusted bridge subject; 1-128 characters |
| displayName | string | Trimmed; 1-100 characters |
| image | string or null | Absolute URL or null |

## Profile Update Result

| Field | Type | Rule |
|---|---|---|
| id | string | Verified owner identifier |
| displayName | string | Persisted normalized display name |
| image | string or null | Persisted avatar URL |
| recoveryEmail | string or null | Existing field; unchanged |
| emailVerified | boolean | Recovery verification time exists |

## State transition

One existing owner row changes only `display_name`, `image`, and `updated_at`. Recovery email, role, disabled state, access version, credentials, and sessions remain unchanged. A missing owner has no transition.

## Invariants

- The row identifier equals the verified owner subject.
- No request field selects another owner.
- Clearing the image stores null.
- Invalid input performs no durable operation.
