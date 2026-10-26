# Application-list query benchmark — 2026-10-26

## Environment and data

- Isolated `postgres:17-alpine` Docker container; no shared or production database was accessed.
- Loaded `src/test/resources/postgres/applications-read-model.sql` with its existing indexes.
- Inserted 50,000 synthetic applications: 40,000 for `owner-a` and 10,000 for `owner-b`, with varied company, position, city, status and dates. Inserted 10,000 stage occurrences (one for every fifth application), then ran `ANALYZE`.
- Ran the production query shapes from `PostgresApplicationReadQuery`: owner count, combined company/status/stage filter count (200 matches), and the default sort with `LIMIT 51 OFFSET 30000`.
- Each query had 20 warm-cache `EXPLAIN (ANALYZE, BUFFERS)` samples in one PostgreSQL session. Values below are database execution times, not HTTP latency. The p95 uses the nearest-rank method.

| Query | Median | p95 | Relevant plan evidence |
| --- | ---: | ---: | --- |
| Owner count | 3.162 ms | 4.421 ms | Sequential scan of 50,000 rows; 816 shared buffers |
| Combined filtered count | 10.280 ms | 10.495 ms | Stage scan and hash aggregate followed by 10,000 primary-key probes; 30,103 shared buffers |
| Deep page | 97.394 ms | 104.310 ms | Scan and sort of 40,000 owner rows; external merge sort used about 5 MB of temporary disk; lateral stage lookup ran 40,000 times |

The deep-page plan also evaluated the stage aggregation for 30,051 rows before applying the final page. This is the main scaling concern. All three measured p95 values are below the existing 500 ms read budget on this synthetic dataset, so this task makes no SQL or schema change. Re-measure on approved production-shaped data before proposing a pagination or index change; preserve exact totals and existing cursor semantics.
