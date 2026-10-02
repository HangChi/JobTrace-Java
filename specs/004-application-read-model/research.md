# Research: Application Read Model Migration

## Slice boundary

**Decision**: Migrate only `GET /api/applications` and `GET /api/applications/{id}`.

**Rationale**: These operations form the core owner-scoped application read model and do not require
write ownership. The interview-enriched `/detail` response crosses into the next bounded context;
mutations depend on optimistic-lock and event-writing rules that deserve a separate specification.

**Alternatives considered**: Migrating the whole applications module was rejected because it would
transfer write ownership and database-function behavior in one step. Migrating only the list was
rejected because core application detail uses the same tables and security boundary and can be
verified independently without adding a writer.

## Database access

**Decision**: Use Spring JDBC with explicit, parameterized PostgreSQL queries and row mappers.

**Rationale**: The legacy behavior relies on lateral joins, filtered aggregates, enum casts, tuple
cursor comparisons, and JSON event history. Explicit SQL preserves those semantics and matches the
existing analytics slice without introducing generated schema state or entity persistence.

**Alternatives considered**: JPA was rejected by repository policy and because it obscures the
existing SQL contract. jOOQ code generation was deferred because the repository does not own the
legacy migration chain; plain dynamic SQL was rejected because sort identifiers must remain
allowlisted and reviewable.

## List normalization and ordering

**Decision**: Preserve the legacy query defaults exactly: trimmed search limited to 200 characters;
city values limited to 100 characters; page at least one; limit clamped to 1–100 with default 50;
unknown enum filters ignored; invalid dates ignored; invalid sort falls back to `latestDate` and
the special default ordering; direction defaults by sort.

Default ordering places `submitted` before all other statuses, then orders by latest date descending
and UUID descending. Explicit sorts use the requested direction plus UUID in that direction as the
deterministic tie-breaker.

**Rationale**: Existing users and tests depend on these defaults, including the difference between
an omitted/invalid sort and an explicit `latestDate` sort.

**Alternatives considered**: Rejecting every invalid optional parameter was rejected because it
would break the current tolerant contract. Offset-only pagination was rejected because the existing
API exposes continuation cursors and uses them for stable traversal.

## Cursor compatibility

**Decision**: Preserve the base64url-encoded UTF-8 JSON cursor containing `value`, UUID `id`, and an
optional integer `statusRank`. Decode strictly; malformed cursor content returns the established
validation problem and never reaches SQL. When a cursor is present, it takes precedence over page
offset, matching the current behavior.

**Rationale**: Existing clients may retain and replay a continuation cursor between requests. A new
opaque format would create an avoidable compatibility break even though both are internal details.

**Alternatives considered**: A new signed cursor was deferred because cursor integrity is not a
security boundary: every query remains owner-scoped and cursor fields are parameterized. Accepting
partial cursor objects was rejected because it can produce unstable or surprising pages.

## Follow-up and date semantics

**Decision**: Compute `latestDate`, `followUpDays`, `needsFollowUp`, and `followUpReason` with the
same Shanghai business-day semantics and 15-day threshold as the legacy service. Inject `Clock` so
tests are deterministic.

**Rationale**: These derived fields appear in both analytics and application contracts and otherwise
change at midnight or when stages overtake the stored application date.

**Alternatives considered**: Using server-local time was rejected because CI and production hosts
may run in UTC. Dropping the fields was rejected because it breaks the response contract.

## Detail aggregation

**Decision**: Fetch an owned application, its ordered stage occurrences, and its complete ordered
event history as one read model. Stage occurrences order by occurrence date then creation time;
events order by occurrence date then creation time descending. Missing and cross-owner rows share
one not-found outcome.

**Rationale**: This reproduces the current detail contract while keeping the application use case
read-only. Including the owner predicate at the root query prevents existence disclosure.

**Alternatives considered**: Returning the application before loading history was rejected because
it creates partial responses. Joining interview summaries was rejected because that belongs to the
interviews context and the explicitly excluded dialog endpoint.

## Contract evidence without deployment

**Decision**: Check representative legacy JSON fixtures into Java test resources and compare exact
deserialized structures. Add Testcontainers tests for owner isolation and all sort/cursor paths.
Completion requires local `./mvnw verify` and pull-request CI, but no canary or production window.

**Rationale**: The user explicitly does not intend to deploy the Java version now. Deterministic
contract and database evidence still allows the migration implementation to progress safely.

**Alternatives considered**: A live cross-service test was rejected because it makes CI depend on a
second runtime. Declaring parity from unit tests alone was rejected because SQL ordering and JSON
mapping require integration evidence.
