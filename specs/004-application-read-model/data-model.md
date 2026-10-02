# Data Model: Application Read Model

Feature 004 creates no tables, migrations, or write model. It maps the existing legacy schema into
owner-scoped immutable responses.

## Application Summary

| Field | Type | Rules |
| --- | --- | --- |
| `id` | UUID string | Existing application identifier; visible only to its owner |
| `companyName` | string | Required existing display value |
| `positionName` | string | Required existing display value |
| `city` | string or null | Optional existing display value |
| `jobUrl` | string or null | Optional existing link value |
| `appliedDate` | date | ISO `YYYY-MM-DD` |
| `type` | application type | One of the six existing values |
| `status` | application status | `submitted`, `offer`, or `refused` |
| `latestDate` | date | Existing aggregate activity date |
| `stages` | stage list | Distinct existing recruitment stages |
| `needsFollowUp` | boolean | True only for active submitted records at the threshold |
| `followUpDays` | non-negative integer | Whole Shanghai business days since latest application or stage activity |
| `followUpReason` | `timeline`, `application`, or null | Source of overdue activity, null when no follow-up is needed |
| `version` | positive integer | Existing optimistic version, exposed but never modified here |

## Application Detail

Application Detail contains every Application Summary field plus:

| Field | Type | Rules |
| --- | --- | --- |
| `notes` | string or null | Existing private notes |
| `stageOccurrences` | stage occurrence list | Ordered by occurrence date then creation order |
| `events` | application event list | Complete history ordered newest first |
| `createdAt` | timestamp string | Existing serialized timestamp |
| `updatedAt` | timestamp string | Existing serialized timestamp |

## Stage Occurrence

| Field | Type | Rules |
| --- | --- | --- |
| `id` | UUID string | Existing occurrence identifier |
| `stage` | recruitment stage | One of eight existing values |
| `occurredOn` | date | ISO `YYYY-MM-DD` |

## Application Event

| Field | Type | Rules |
| --- | --- | --- |
| `id` | UUID string | Existing event identifier |
| `type` | string | Existing event kind; read without inventing a new enum |
| `occurredOn` | date | Business date associated with the event |
| `before` | JSON value | Existing prior-state snapshot, possibly null |
| `after` | JSON value | Existing resulting-state snapshot, possibly null |
| `createdAt` | timestamp string | Existing serialized timestamp |

## Application Page

| Field | Type | Rules |
| --- | --- | --- |
| `items` | application summary list | Ordered and owner-scoped |
| `nextCursor` | string or null | Present only when another cursor page exists |
| `total` | non-negative integer | Count for the full filtered owner-scoped result set |
| `page` | positive integer | Normalized requested page even when a cursor is used |
| `limit` | integer | Normalized range 1–100 |

## Application List Criteria

| Field | Type | Normalization |
| --- | --- | --- |
| `q` | optional string | Trimmed, first 200 characters, absent when empty |
| `status` | status list | Unknown values removed |
| `type` | application type list | Unknown values removed |
| `stage` | recruitment stage list | Unknown values removed |
| `city` | string list | Each value limited to 100 characters |
| `appliedFrom` | optional date | Invalid value ignored |
| `appliedTo` | optional date | Invalid value ignored |
| `sort` | sort key | `company`, `position`, `appliedDate`, or `latestDate`; safe default otherwise |
| `direction` | direction | `asc` or `desc`; defaults to ascending for names and descending for dates |
| `cursor` | optional cursor | Strict base64url JSON structure |
| `page` | integer | Minimum 1, default 1 |
| `limit` | integer | Clamped to 1–100, default 50 |

## Cursor

The decoded cursor has `value` (non-empty string), `id` (UUID), and optional `statusRank` (integer
0–2). It is a navigation token, not an authorization token. The owner always comes from the signed
principal and is applied independently.

## Existing Relationships

- One owner has many applications.
- One application has many stage occurrences.
- One application has many events.
- Every returned relationship is reachable only through an application owned by the authenticated
  principal.

## State and Ownership

There are no state transitions in feature 004. All entities are read-only projections. The legacy
service remains the only writer and the legacy migration chain remains the schema authority.
