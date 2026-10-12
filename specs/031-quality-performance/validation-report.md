# Validation checkpoint — 2026-10-12

- Pre-change frontend bundle: one 345.19 KB JavaScript file (98.46 KB gzip). After route splitting: 233.92 KB initial JavaScript (73.74 KB gzip) plus page chunks loaded on demand.
- `npm run lint`, `npm test` (77/77), and `npm run build` pass.
- `npm run test:performance` passes all three checks after reserving overview space; observed CLS was 0 in the final local run. One preceding run measured CLS 0.1073 and identified `.application-filters` as the shifted element.
- `npm run test:coverage` still fails the existing 80% global thresholds: lines 89.36%, branches 66.96%, functions 69.32%, statements 76.57%. This is open work.
- `./mvnw verify` passes (744 tests, 0 failures, 0 errors, 402 skipped). The skipped tests require Docker; the local Docker daemon is unavailable.
- `./mvnw -Ppackaged-test verify` passes the packaged application test. An incremental `./mvnw -DskipTests package` produced a JAR with exactly the 20 files present in `frontend/dist` and no stale static assets.
- Application-list query plan and deep-page latency measurements are pending. No SQL or schema changes were made.
