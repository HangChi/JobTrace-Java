# Data Model: Java Migration Foundation

The foundation creates no new production business tables. It defines migration-control concepts that later slices must record in specifications, tests, deployment configuration, or an operational registry.

## Migration Slice

| Field | Description | Validation |
|---|---|---|
| `id` | Stable migration identifier | Unique, lowercase slug |
| `module` | Existing business module | One of the documented JobTrace modules |
| `capabilities` | Endpoints and background actions in scope | Non-empty and bounded |
| `legacyOwner` | Service currently serving traffic | Required until retirement |
| `targetOwner` | New service prepared to serve traffic | Required before verification |
| `writeOwner` | Sole service allowed to mutate each aggregate | Exactly one owner per aggregate |
| `state` | Migration lifecycle state | Uses transition rules below |
| `rollbackRoute` | Reversible traffic or deployment action | Required before activation |
| `evidence` | Contract and acceptance results | Required before activation |

### State transitions

```text
planned -> implemented -> verified -> active -> observed -> retired
                  \           |
                   -> rolled-back <-
```

- `verified` requires all contract cases to pass.
- `active` requires a single declared write owner and a tested rollback route.
- `retired` requires the observation period to complete without a blocking regression.
- `rolled-back` preserves evidence and can return to `implemented` after correction.

## Contract Baseline

| Field | Description |
|---|---|
| `operationId` | Stable operation name shared by test and documentation |
| `request` | Method, path, headers, body, and validation rules |
| `response` | Success status, headers, and body shape |
| `errors` | Safe error statuses, codes, and non-sensitive messages |
| `authorization` | Anonymous, user, owner, or administrator requirements |
| `persistenceEffects` | Expected database changes and invariants |
| `performanceBudget` | Representative latency and volume target |

## Schema Baseline

| Field | Description |
|---|---|
| `legacyHead` | Last migration owned by the existing repository |
| `legacyChecksumEvidence` | Verification output for the complete old chain |
| `newBaselineVersion` | First version recognized by the new migration tool |
| `approvedAt` | Review timestamp |
| `approvedBy` | Maintainer identity |
| `rollbackBackup` | Verified backup or restoration reference |

The schema baseline cannot become active until an empty-database replay and an existing-database adoption have both been tested.

## Build Artifact

| Field | Description |
|---|---|
| `applicationVersion` | Immutable release version |
| `backendRuntime` | Required Java runtime |
| `frontendDigest` | Digest of embedded browser assets |
| `sourceRevision` | Git commit used to build the artifact |
| `checks` | Backend, frontend, security, and contract results |

