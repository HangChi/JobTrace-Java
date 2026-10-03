# Data Model: Account Session Revocation

## SessionRevocationCommand

| Field | Type | Rules |
|---|---|---|
| `ownerId` | string | Derived only from the verified bridge principal; 1-128 characters |
| `sessionId` | string | Request body value; opaque, case-sensitive, 1-255 characters |

## SessionRevocationResult

| Field | Type | Rules |
|---|---|---|
| `revoked` | boolean | Always true; returned only after one owned row is deleted |

## State transition

`owned session exists` → atomic owner-and-ID delete → `{ revoked: true }`

If no matching owned row exists, durable state remains unchanged and the result is a non-disclosing not-found problem. Concurrent commands can cross the successful transition only once.
