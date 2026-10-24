# Validation: Application Data Import

## Automated checks

From the Java repository run `./mvnw verify`. Focused tests should cover CSV/XLSX parsing and validation, owner-scoped preview storage and duplicate detection, confirmation status and per-row results, malformed/foreign/expired requests, and a compatible application export/import round trip.

## Manual contract scenarios

1. Upload a valid CSV with Chinese headers and one invalid row. Expect HTTP 201, a private preview, one valid and one invalid row, and no new application.
2. Upload the same data with custom headers and a one-to-one `mapping`; pass the old `replaceBatchId`. Expect the new preview to be usable and the old one expired only after the new save succeeds.
3. Confirm only the valid row. Expect `created=1`, `skipped=1` and an `imported` application event. A second confirmation returns conflict.
4. Confirm as another owner and with an expired batch. Expect no application writes and safe errors.
5. Export the created application as XLSX and preview it again. Expect recognized business columns and equivalent base application values.

Production routing and scheduler activation remain outside this stage.

## Validation evidence (2026-10-08)

- `./mvnw -q -Dlogging.level.root=OFF -Dspring.main.banner-mode=off verify` passed: 696 tests, 0 failures, 0 errors, 1 skipped. The 026 import test classes contributed 25 passing tests.
- JaCoCo counters for the new 026 production source files: 419/425 covered lines (98.6%) and 241/285 covered branches (84.6%), both above the 80% gate. The shared exception-handler edit is additionally exercised by `GlobalExceptionHandlerTest`.
- Local one-row preview/confirm benchmark: 5 warmups followed by 30 samples, p95 preview 2 ms and confirm 1 ms. These measurements exclude network transfer and do not predict production latency.
- The separate 10,000-row CSV HTTP preview test returned all 10,000 outcomes, created no applications and took 369 ms in one local run. It also passed independently after the timing assertion was added.
