# Job Market Operations (Feature 027)

The Java service reads marketplace companies, campaigns, posts, sources, candidates and sync-run history from the configured shared PostgreSQL database. The existing JobTrace service remains the sole owner of ATS/WeChat collection, default-catalog bootstrap, source leases, synchronization, post normalization and projection refresh. Java does not open marketplace URLs or run scheduled ingestion.

Java provides owner-scoped campaign favorites and administrator-only source metadata management, candidate review, catalog inspection and sync-run history. The administrator catalog is assembled from current `job_market_companies`, directory campaigns and source rows; it is not a bundled copy of default catalog JSON. Candidate approvals are transactional and update the same legacy tables. These explicit user/admin mutations require a route-ownership cutover before both services can serve the same write path in production.

`POST /api/admin/job-market/sources/{sourceId}/sync` returns `410 ingestion_owned_externally` without claiming a lease or writing a run. The existing service remains responsible for discovery, research, ATS scans, WeChat collection and manual sync triggers. Java read endpoints do not trigger these actions. Frontend controls must present ingestion as externally managed, rather than interpreting the Java response as a successful sync.

Database credentials are provided through existing runtime configuration; no credentials, data dumps or new production migrations are included here. The old service remains the production route owner until an explicit routing decision. Rolling back this slice removes Java handling while retaining all marketplace rows and the legacy ingestion worker.

See [the feature contract](../../specs/027-job-market-operations/contracts/openapi.yaml) and [validation guide](../../specs/027-job-market-operations/quickstart.md).

Stage 029 confirmed this split against the legacy API inventory: Java exposes shared catalog,
candidate, source metadata and run-history APIs but no ATS/WeChat collector or internal sync
writer. See the [route matrix](../../specs/029-code-hardening/contracts/route-matrix.md).
