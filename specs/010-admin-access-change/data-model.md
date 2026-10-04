# Data Model: Administrator Access Changes

Feature 010 adds no production table or migration. It sends a validated command to the existing atomic access-change operation and maps the returned durable result.

## Access Change Command

| Field | Type | Rules |
|---|---|---|
| targetUserId | opaque string | 1–128 characters; supplied by path |
| requestId | UUID | Required idempotency key |
| expectedVersion | positive integer | Must match current access version |
| action | enum | `promote_admin`, `demote_admin`, `disable_user`, `enable_user` |
| reason | string | Trimmed; 10–500 characters |
| confirmSelf | boolean | Defaults to false |

## Access State

| Field | Type | Meaning |
|---|---|---|
| role | enum | `user` or `admin` |
| disabled | boolean | Whether account access is blocked |
| accessVersion | positive integer | Monotonic optimistic-concurrency version |

## Command Result

| Field | Type | Meaning |
|---|---|---|
| outcome | enum | `succeeded`, `denied`, `conflict`, or `failed` |
| failureCode | nullable string | Stable policy/conflict code |
| auditEventId | UUID/string | Existing append-only audit record |
| replayed | boolean | Whether the original result was returned |
| userId | nullable opaque string | Target returned by the durable operation |
| role | nullable enum | Latest safe role |
| disabled | nullable boolean | Latest safe status |
| accessVersion | nullable positive integer | Latest safe version |

## State Transitions

| Action | Allowed current state | Next state | Additional rule |
|---|---|---|---|
| promote_admin | active `user` | active `admin` | Disabled targets are rejected |
| demote_admin | `admin` | `user`, same disabled flag | Self-action needs confirmation; cannot remove last active admin |
| disable_user | enabled account | same role, disabled | Self-action needs confirmation; cannot disable last active admin; sessions deleted |
| enable_user | disabled account | same role, enabled | Sessions remain absent |

All successful transitions increment `accessVersion` once. Rejections retain the prior state. Every authorized first attempt appends one audit event; identical retries append none.
