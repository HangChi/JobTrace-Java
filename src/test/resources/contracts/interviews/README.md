# Synthetic private interview contract fixtures

These fixtures freeze the JSON shape of the existing JobTrace private interview read API, without copying production data. Sources: `JobTrace/src/modules/interviews/application/contracts.ts`, `list-query.ts`, `infrastructure/postgres-interview-repository.ts`, and `src/shared/pagination/cursor.ts` at the migration baseline. All names, IDs, dates, and content here are invented.

Dates are ISO `yyyy-MM-dd`; timestamps are UTC ISO strings. List order is `interviewedOn DESC, id DESC`. The cursor is unpadded base64url of UTF-8 JSON `{"value":"yyyy-MM-dd","id":"uuid"}` for the last returned row. Regenerate fixture cursors from that exact JSON, not from a live service. `total` is the filtered owner count before cursor navigation. Empty collections stay arrays and absent optional values stay JSON null. Structural contract comparisons ignore object-key order but not array order or null fields.
