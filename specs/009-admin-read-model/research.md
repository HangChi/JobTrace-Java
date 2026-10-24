# Research: Administration Read Model

## Scope boundary

- **Decision**: Migrate four exact GET shapes: summary, user list, opaque-ID user detail, and audit list. Keep PATCH access changes and all job-market administration outside 009.
- **Rationale**: This completes a useful read-only administration journey without transferring a security-sensitive writer.
- **Alternatives considered**: Summary only was too small; access mutation would require idempotency, locking, last-admin protection, and a separate write-ownership review.

## Authorization

- **Decision**: Reuse the signed bridge and require the verified `ADMIN` role from assertion details for every operation.
- **Rationale**: Java already validates subject, role, access version, method, path, request ID, expiry, signature, configured key, and replay. No public header can select a role.
- **Alternatives considered**: Database role lookup would introduce a second authorization source; accepting an admin header is prohibited.

## Compatibility

- **Decision**: Preserve legacy field names, fallbacks, query defaults/bounds, Asia/Shanghai day boundaries, stable ordering, last-page clamping, detail page size 10, and partial summary availability.
- **Rationale**: The current browser depends on those semantics and uses opaque text user identifiers.
- **Alternatives considered**: UUID-only IDs and zero-item out-of-range pages would break existing behavior.

## Persistence

- **Decision**: Use parameterized Spring JDBC reads over unchanged legacy tables. Keep counts/list rows set-based and compose detail with bounded queries.
- **Rationale**: The repository forbids a mechanical JPA replacement and the legacy schema remains authoritative.
- **Alternatives considered**: JPA adds mappings without value; new projections or migrations would transfer schema ownership prematurely.

## Failure and observability

- **Decision**: Summary sources fail independently; other storage failures use safe problems. Metrics include only operation, bounded outcome, and latency. All responses use `private, no-store`.
- **Rationale**: Summary usability should survive one failed source, while sensitive identifiers and content must not enter diagnostics or caches.
- **Alternatives considered**: Returning partial rows risks misleading operators; identifier labels create cardinality and privacy risks.

## Performance

- **Decision**: Use a reproducible 1,000-user data set and enforce p95 at or below 500 ms with fixed query counts.
- **Rationale**: This matches the constitutional read budget and catches per-row query regressions.
- **Alternatives considered**: Production-derived data is prohibited; an unbounded benchmark would not be reproducible.
