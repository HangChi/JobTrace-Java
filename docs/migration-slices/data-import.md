# Application Data Import (Feature 026)

The Java data-transfer module now provides the existing private two-step import contract:

- `POST /api/imports/preview` accepts multipart `file`, optional JSON `mapping`, and optional `replaceBatchId`. It parses a bounded UTF-8 CSV or first-sheet XLSX file, validates application rows, finds owner-scoped duplicate candidates, and stores a 24-hour preview without creating applications.
- `POST /api/imports/{id}/confirm` accepts `decisions: [{rowNumber, action}]` and returns created, skipped and failed counts with row outcomes. It calls the existing `confirm_import_batch_for_owner` function, which locks the batch, creates selected valid rows with `imported` events, isolates row failures and prevents replay.

The verified browser/bridge principal supplies the owner. Caller-supplied ownership is ignored. Responses carry `private, no-store`; the import file and row content are not logged. Files are limited to 5 MiB, 10,000 data rows, 100 columns and 200,000 cells. CSV uses strict UTF-8; XLSX formulas are never evaluated. Unknown or duplicate column mappings are rejected.

The existing PostgreSQL schema and legacy function remain the sole persistence contract. Java and the old service must not both become active writers for these routes without a separate cutover. The old service remains production route owner; reverting this branch removes Java route handling and leaves old previews to expire. No schema, frontend, CI/CD, deployment or production routing change is included.

See [the feature contract](../../specs/026-data-import/contracts/openapi.yaml) and [validation guide](../../specs/026-data-import/quickstart.md).
