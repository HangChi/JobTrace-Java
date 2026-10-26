# Validation checkpoint — 2026-10-12

- Pre-change frontend bundle: one 345.19 KB JavaScript file (98.46 KB gzip). After route splitting: 233.92 KB initial JavaScript (73.74 KB gzip) plus page chunks loaded on demand.
- `npm run lint`, `npm test` (108/108), and `npm run build` pass after the coverage test batch.
- `npm run test:performance` passes all three checks after reserving overview space; observed CLS was 0 in the final local run. One preceding run measured CLS 0.1073 and identified `.application-filters` as the shifted element.
- `npm run test:coverage` passes all existing 80% global thresholds: lines 95.18%, branches 80.30%, functions 85.44%, statements 88.00%. The new tests exercise administrator archives, recruitment filtering and favorites, imports, analytics recovery, interview workflows, application editing and listing, and profile settings.
- `./mvnw verify` passes (744 tests, 0 failures, 0 errors, 402 skipped). The skipped tests require Docker; the local Docker daemon is unavailable.
- `./mvnw -Ppackaged-test verify` passes the packaged application test. An incremental `./mvnw -DskipTests package` produced a JAR with exactly the 20 files present in `frontend/dist` and no stale static assets.
- Application-list query plan and deep-page latency measurements are pending. No SQL or schema changes were made.

## Follow-up — 2026-10-26

- Docker-backed `./mvnw verify` passed: 744 tests, 0 failures, 0 errors and 1 pre-existing skip; JaCoCo and Checkstyle passed. This supersedes the Docker-unavailable observation above.
- `npm run lint`, `npm test` (108/108), `npm run test:coverage` and `npm run build` passed. Global coverage remained 95.18% lines, 80.30% branches, 85.44% functions and 88.00% statements.
- `npm run test:performance` passed five browser checks, including guest deep links and ordinary-user admin denial; the observed Core Web Vitals sample was LCP 104 ms, INP 48 ms and CLS 0.
- The [application-list query benchmark](query-benchmark.md) measured count, combined filter and deep-page plans on 50,000 synthetic PostgreSQL rows. Their 20-sample database execution p95 values were 4.421 ms, 10.495 ms and 104.310 ms respectively. No SQL or schema change was made.
