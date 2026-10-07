# Research: Application Data Import

- **Compatibility source**: The existing `src/modules/data-transfer` import workflow, `docs/data-transfer.md`, and `src/app/api/imports/**` in the legacy repository. Preserve multipart `file`, optional JSON `mapping`, optional `replaceBatchId`, and confirmation `decisions`.
- **Persistence**: Existing `import_batches` and `import_rows` tables, plus `confirm_import_batch_for_owner(actor_id, batch_id, decisions)` from the current migration head. Do not add a migration or enable Flyway. The database function owns batch locking, per-row savepoints, creation events and once-only confirmation.
- **File handling**: Use bounded multipart input; Commons CSV is not installed, so use the existing Apache POI dependency for first-sheet XLSX and a small strict RFC 4180 CSV parser for UTF-8. Do not evaluate formulas or trust MIME type alone.
- **Identity**: Derive owner from the verified principal through `BridgePrincipalOwner`; browser session and signed bridge support remain as implemented. Never accept an owner field in requests.
- **Validation**: Align with `ApplicationCommandRequest`/legacy application schema. Normalize aliases, defaults and stage history before saving a preview. Unrecognized fields remain unmapped.
- **Duplicate detection**: One set-based owner-scoped SQL query over valid candidate keys, returning IDs only for owned applications; no per-row database round trips.
- **Response safety**: Field-level validation issues may describe submitted row fields, but storage and unexpected failures use generic problem responses with request IDs. Preview responses are `private, no-store`.
- **Performance**: Measure representative small requests against 500 ms read/1 s write goals; report large-file timings separately because parsing and confirmation scale with row count.
