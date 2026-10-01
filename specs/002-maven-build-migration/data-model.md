# Data Model: Maven Build Migration

This feature introduces no application or database entities. It preserves one build artifact
contract whose values are verified during packaging.

## Build Artifact

| Field | Description | Validation |
| --- | --- | --- |
| `artifactId` | Stable executable-JAR name | `jobtrace-0.1.0-SNAPSHOT.jar` |
| `javaVersion` | Runtime and compilation baseline | Java 21 |
| `sourceRevision` | Git commit represented by the artifact | Nonblank commit ID or explicit `unknown` fallback |
| `frontendAssetDigest` | Aggregate SHA-256 of compiled frontend assets | 64 lowercase hexadecimal characters |
| `frontendEntryPoint` | Embedded browser shell | `BOOT-INF/classes/static/index.html` exists |
| `runtimeDependencies` | Software required inside the production image | Java only; Node.js and npm absent |
| `verificationState` | Results of required release gates | Backend, frontend, packaged, and Docker checks all pass |

## Lifecycle

```text
source checkout
  -> verified dependencies
  -> backend compilation and tests
  -> fresh frontend compilation
  -> resource embedding and metadata calculation
  -> executable JAR
  -> packaged smoke test
  -> Java-only container smoke test
```

A failure at any stage prevents the artifact from being considered releasable. No state is stored
in PostgreSQL and no migration is required.
