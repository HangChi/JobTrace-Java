# Data Model: Signed Identity Bridge and Analytics Activation

This feature adds no persistent business entities and no PostgreSQL schema changes. Assertions are
request-scoped, replay records expire automatically, and activation evidence is reviewed operational
documentation.

## IdentityAssertion

| Field | Type | Validation |
| --- | --- | --- |
| `version` | integer | Exactly `1` |
| `issuer` | string | Exact configured legacy issuer |
| `audience` | string | Exact configured Java audience |
| `subject` | string | Non-blank legacy user ID, at most 128 characters |
| `role` | enum | `user` or `admin` |
| `accessVersion` | integer | Zero or greater |
| `requestId` | UUID | Equals effective `x-request-id` |
| `method` | string | Exact uppercase `GET` |
| `path` | string | Exact `/api/analytics/summary` |
| `issuedAt` | instant | Not in future beyond skew |
| `notBefore` | instant | Not after current time plus skew |
| `expiresAt` | instant | After issue time and at most 30 seconds later |
| `tokenId` | string | At least 128 bits of unpredictable uniqueness |
| `keyId` | string | Present in JWS header and configured allowlist |

## BridgeIdentity

Contains validated `subject`, `role`, and `accessVersion`. It exists only after signature, claims,
request binding, and replay claim succeed. Its subject becomes the principal name used by analytics.

## SigningKeyDescriptor

| Field | Type | Validation |
| --- | --- | --- |
| `keyId` | string | 1–64 URL-safe characters; unique |
| `secret` | external bytes | At least 32 random bytes; never logged or committed |
| `state` | enum | `current` or `previous`; issuer has one current key |

Lifecycle: `current -> previous -> removed`. Java acceptance follows the configured key map; the
issuer signs only with its current key.

## ReplayRecord

| Field | Type | Validation |
| --- | --- | --- |
| `key` | string | Namespaced from issuer and token ID |
| `claimed` | boolean | First atomic claim succeeds; later claims fail |
| `expiresAt` | instant | Assertion expiry plus skew |

No subject, role, path, or response data is stored.

## CanaryObservation

| Field | Type | Validation |
| --- | --- | --- |
| `requestId` | UUID | Correlates sanitized diagnostics |
| `implementation` | enum | `legacy` or `java` |
| `statusClass` | enum | `2xx`, `4xx`, `5xx`, timeout |
| `duration` | duration | Non-negative |
| `mismatchClass` | enum | none, status, schema, value, ordering, unavailable |
| `sourceRevision` | string | Reviewed immutable revision |
| `observedAt` | instant | Inside declared observation window |

## ActivationDecision

State follows `VERIFIED -> ACTIVE -> OBSERVED` or `VERIFIED/ACTIVE -> ROLLED_BACK`. Evidence covers
security tests, owner isolation, parity, latency, rollback rehearsal, seven-day observation, CI, and
the Solo-Maintainer Mode self-review. Repository state alone never changes production routing.
