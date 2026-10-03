# Feature 009: administration read model

Feature 009 adds four administrator-only compatibility reads: operational summary, managed-user
directory, managed-user detail, and immutable access audit history. The current JobTrace service
remains the sole account-access writer, identity/session authority, schema owner, frontend owner,
deployment owner, and production traffic owner.

Java accepts only signed, one-time, method/path/request-ID-bound bridge assertions carrying the
verified administrator role. Ordinary users and invalid or replayed assertions receive no
administration data. Responses and errors use `private, no-store`.

The implementation reads unchanged PostgreSQL tables through parameterized Spring JDBC. It
preserves Shanghai calendar windows, the gap-free 30-day activity trend, independent summary
availability, user display fallbacks, stable pagination with last-page clamping, bounded nested
content, stage snapshots, and immutable audit identity snapshots. It never invokes the access
change function and introduces no production migration.

Local validation uses synthetic fixtures and PostgreSQL 17 Testcontainers. It covers strict query
normalization, admin authorization, contract parsing, list/detail/audit persistence behavior,
read-only checks, architecture boundaries, diagnostic label bounds, and a reproducible 1,000-user
p95 budget. No CI/CD, deployment, canary, or routing work is included.
