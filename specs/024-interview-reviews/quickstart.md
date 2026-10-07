# Local Validation

1. Start Docker for PostgreSQL Testcontainers; no real user database is modified.
2. Run `./mvnw verify` in the Java repository.
3. Check private create, current-version edit, stale conflict, delete and foreign-owner not found.
4. Check publication, anonymous/attributed feed/detail, latest/hot cursors, view count, unpublication and hidden URL.
5. Check like/unlike, comments, invalid content and rejection after unpublication.
6. Confirm changed-code JaCoCo line/branch coverage and representative p95 timing below.

Frontend, CI/CD, deployment and production cutover are outside this stage.

## Local evidence (2026-10-07)

- `./mvnw verify -q -Dlogging.level.root=ERROR` passed with PostgreSQL 17 Testcontainers.
  The subsequently added exception-handler edge tests passed with
  `./mvnw -q -Dtest=PublicInterviewExceptionHandlerTest test jacoco:report`.
- JaCoCo counters for new 024 production classes: 482/506 lines (95.3%) and
  219/269 branches (81.4%). The calculation includes private command, public read,
  engagement and HTTP adapter classes, including their nested records.
- `PublicInterviewPerformanceTest`: Spring MockMvc, local PostgreSQL Testcontainer,
  fixed fixture with two public reviews and one existing comment, one reader, five
  warmups, then 30 sequential samples per operation. Sorted sample index 28 is p95.
  Feed GET = 2 ms, detail GET = 2 ms, like-toggle POST = 1 ms. All returned HTTP 200.
  These are local compatibility measurements, not production latency claims.
