# Data Model: Private Interview Read Model

Feature 005 adds no production tables, schema migration, or write model. It projects the existing legacy schema into immutable owner-scoped responses.

## Interview Review Summary

| Field | Type | Rule |
| --- | --- | --- |
| `id` | UUID string | Visible only to its owner |
| `applicationId` | UUID string | Referenced application must be owned by the same owner |
| `stageOccurrenceId` | UUID string or null | Null after unlinking or for a review without a retained occurrence |
| `companyName`, `positionName` | strings | Existing application display values |
| `stage` | interview stage | Linked occurrence stage, otherwise stored snapshot |
| `interviewedOn` | date string | ISO business date |
| `status` | review status | `draft`, `pending_review`, `completed` |
| `roundResult` | round result | `pending`, `passed`, `failed` |
| `linked` | boolean | True exactly when `stageOccurrenceId` is present |
| `questionCount`, `actionCount` | non-negative integers | Counts for this review only |
| `visibility` | visibility | `private` or `public`; does not override private owner scoping |
| `authorMode` | author mode | `anonymous` or `attributed` |
| `publishedAt` | timestamp string or null | Null when unpublished |

The permitted interview stages are `assessment`, `interview_1`, `interview_2`, `interview_3`, `hr_interview`, and `final_interview`.

## Interview Review Detail

Detail contains every summary field plus:

| Field | Type | Rule |
| --- | --- | --- |
| `format` | `online`, `offline`, `phone`, or null | Existing nullable format |
| `durationMinutes` | integer or null | Existing nullable duration |
| `interviewerNotes`, `highlights`, `gaps` | strings or null | Private review content; never logged |
| `version` | positive integer | Existing optimistic version, read but not modified |
| `questions` | ordered question list | Empty array when absent; `sort_order` ascending |
| `actionItems` | ordered action-item list | Empty array when absent; `sort_order` ascending |
| `createdAt`, `updatedAt` | timestamp strings | Preserve existing serialization contract |

### Interview Question

`id`, `category` (`technical`, `project`, `behavioral`, `system_design`, `other`), `question`, nullable `originalAnswer`, `followUpNotes`, `improvedAnswer`, and nullable `selfRating` (1–5). Each question belongs to exactly one review and has a stable saved order.

### Interview Action Item

`id`, `content`, and `completed`. Each action item belongs to exactly one review and has a stable saved order.

## Interview Page and Criteria

| Field | Type | Rule |
| --- | --- | --- |
| `items` | summary list | Owner-scoped, sorted by interview date descending then UUID descending |
| `nextCursor` | string or null | Present only when another matching page exists |
| `total` | non-negative integer | Full filtered owner count before cursor |
| `limit` | integer | 1–100, default 50 |

Criteria include optional `applicationId`, trimmed `q` up to 200 characters, repeated valid `status`, `stage`, `result`, and `publication` filters, optional inclusive `interviewedFrom`/`interviewedTo` dates, optional cursor, and limit. Invalid enum/date/UUID/limit inputs are rejected. A decoded cursor has `value` (ISO date) and `id` (UUID), encoded as base64url JSON. It is never an authority source.

## Stage Interview Summary and Application Dialog

The application dialog returns the feature 004 `ApplicationDetail` unchanged plus an `interviews` array. Each stage summary contains `id`, `stage`, `interviewedOn`, `status`, `questionCount`, and nullable `stageOccurrenceId`. The array is ordered by interview date descending then UUID descending and is empty when the owned application has no reviews.

## Existing Relationships and Ownership

- One owner has many applications and interview reviews.
- One application has many interview reviews; a review optionally links one stage occurrence.
- One review has many ordered questions and action items.
- `interview_reviews.owner_id` and the joined `applications.owner_id` must both match the trusted principal where a cross-table read is performed. Child reads occur only for an owned review and should retain an owner-bound join as defense in depth.
- Missing and cross-owner review/application identifiers are indistinguishable externally.

## State and Schema Boundary

There are no state transitions in 005. The existing service is the sole writer of reviews, questions, action items, publication/engagement state, applications, and migration history. Java Flyway remains disabled for the legacy database.
