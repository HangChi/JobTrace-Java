# Local Validation

1. Start Docker for PostgreSQL Testcontainers; tests use a disposable database and fake mail.
2. Run `./mvnw verify` in the Java repository.
3. Check owner-scoped create/edit/cancel; stale versions, foreign links and invalid times must not write.
4. Check complete/snooze/reopen state and timestamps, email retry and settings update.
5. Check unauthorized delivery claims nothing; authorized batches obey limits, lease, attempt cap and finalization.
6. Confirm changed-code JaCoCo coverage and representative p95 latency below.

No frontend, CI/CD, deployment, real mail or production scheduler activation is included.

## Local evidence (2026-10-07)

- `./mvnw verify -q -Dlogging.level.root=OFF -Dspring.main.banner-mode=off`
  passed with PostgreSQL 17 Testcontainers. Mail is replaced by a test double;
  the webhook adapter is separately tested with an in-process HTTP client mock.
- JaCoCo counters for new 025 production classes: 368/377 lines (97.6%) and
  168/199 branches (84.4%). This includes command, delivery, preferences,
  validation and HTTP adapters. Modified shared read/security code is exercised
  by the full suite, but is not included in this new-class aggregate.
- `ReminderOperationPerformanceTest`: local PostgreSQL Testcontainer, 41 total
  reminder rows after warmup and samples, one authenticated MockMvc reader,
  five warmups followed by 30 sequential samples per operation. Sorted sample
  index 28 is p95. Active read GET = 1 ms; create POST = 1 ms. These are local
  compatibility measurements, not production latency claims.
