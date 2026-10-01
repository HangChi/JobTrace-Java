# Legacy database baseline

The Java service treats the existing JobTrace PostgreSQL schema as externally owned. Flyway
must remain disabled until a baseline is reviewed and explicitly approved. Phase 5 introduces
no schema changes and does not create `flyway_schema_history`.

## Candidate snapshot

Captured on 2026-10-01 from `/Users/songhangchi/Project/JobTrace`:

| Evidence | Value |
| --- | --- |
| Source Git revision | `c9e00dd685344c2fc054212d4eb11e5d558a0f7a` |
| Migration count | 66 |
| Candidate head | `supabase/migrations/20260929000100_spring_recruitment_type.sql` |
| Candidate head SHA-256 | `d82ec5da0e0729f0aafad2c38241b3dacb3f149d6c53f8416964c1ce17f66a15` |
| Ordered migration-chain SHA-256 | `323731860c40e6230bbd8ad6baf670daa0fd35be0b390d631a61e7da97aaf4de` |
| Approval status | **Not approved** |
| Approved by / at | Unset |
| Backup reference | Unset |

The source checkout was dirty when captured, and the candidate head migration was untracked.
These values are evidence for review, not an approved Flyway baseline. The head must first be
committed in the legacy repository, then the manifest and hashes must be recaptured.

## Capture procedure

Run these commands from a clean, pinned legacy checkout:

```bash
git status --short
git rev-parse HEAD
find supabase/migrations -maxdepth 1 -type f -name '*.sql' | sort
find supabase/migrations -maxdepth 1 -type f -name '*.sql' -print0 \
  | sort -z | xargs -0 shasum -a 256
find supabase/migrations -maxdepth 1 -type f -name '*.sql' -print0 \
  | sort -z | xargs -0 shasum -a 256 | shasum -a 256
```

Store the complete ordered manifest with the approval record. Reviewers must verify that the
database backup can be restored, all migrations replay successfully into an empty PostgreSQL
database, and an existing production-shaped database can be adopted without executing legacy
DDL a second time.

## Approval and activation gate

Before enabling Flyway, record all of the following in a reviewed change:

1. A clean legacy revision and committed migration head.
2. The full ordered checksum manifest and reviewer identity.
3. Empty-database replay evidence and existing-database adoption evidence.
4. A tested backup/restore reference and maintenance rollback procedure.
5. The exact Flyway baseline version and the environments where it is permitted.

Until that change is approved, keep `jobtrace.migration.flyway-enabled=false`; the Java runtime
may issue owner-scoped reads but must not mutate or claim ownership of the legacy schema.
