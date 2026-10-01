# Java Migration Foundation Validation Report

**Validated**: 2026-10-01

**Target branch**: `main`

**Validated baseline revision**: `59fd3b6877ced431c5e0e5ca7ff7de40d44fcf98`

**Convergence review**: [PR #13](https://github.com/HangChi/JobTrace-Java/pull/13)

**Environment**: macOS, Java 21.0.12, Maven Wrapper 3.9.16, Node.js 24, Docker Desktop

## Result

The complete clean-clone quickstart passed. The foundation remains deployable as one Java artifact,
the analytics pilot remains read-only and owner-isolated, and all configured coverage and static
analysis gates passed.

## Evidence

| Check | Result | Evidence |
|---|---|---|
| Maven Wrapper integrity | PASS | Wrapper 3.3.4 JAR matched the pinned SHA-256 before execution |
| Backend test suite | PASS | 39 tests, 0 failures, 0 errors, 1 expected packaged-profile skip |
| Backend coverage | PASS | JaCoCo line and branch thresholds met |
| Backend static analysis | PASS | 0 Checkstyle violations |
| Read performance smoke | PASS | Health and analytics representative p95 samples stayed within 500 ms |
| Database failure readiness | PASS | Unavailable PostgreSQL returns HTTP 503 within the 10-second SC-004 budget |
| Core Web Vitals | PASS | Chromium lab run: LCP 84 ms, INP 16 ms, CLS 0 |
| Frontend lint | PASS | `oxlint` completed without findings |
| Frontend tests | PASS | 12 tests across 3 files, including axe accessibility checks |
| Frontend coverage | PASS | 100% statements/functions/lines and 84.61% branches |
| Frontend production build | PASS | TypeScript and Vite production build completed |
| Embedded browser assets | PASS | Executable JAR contains the compiled frontend |
| Quickstart | PASS | `./scripts/test-quickstart.sh` ended with `Quickstart verification passed.` |

## Security and repository controls

- GitHub dependency review rejects newly introduced dependencies with moderate-or-higher advisories.
- Gitleaks scans pull requests and pushes to `main` using full repository history.
- `main` requires pull requests, linear history, resolved conversations, protected history, and the
  documented CI and security status checks.
- The repository has exactly one human maintainer with write access, so Constitution v1.1.0
  Solo-Maintainer Mode applies. `main` requires zero approving reviews until a second human receives
  write access; the pull-request, CI, security, conversation-resolution, linear-history, and
  protected-history gates remain mandatory.
- No production credentials, database dumps, local environment files, generated outputs, or
  frontend dependencies are included in the change.

## Solo-maintainer self-review

The sole maintainer approved Solo-Maintainer Mode on 2026-10-01. PR #13 was reviewed against the
001 specification and the four constitutional principles before merge:

| Review area | Result | Assessment |
|---|---|---|
| Specification | PASS | T047-T051 close the identified performance, readiness, lifecycle, governance, and validation gaps without expanding the migration slice |
| I. Maintainable Code | PASS | Changes stay within existing modules, use bounded configuration, and add focused tests and operating documentation |
| II. Testing | PASS | Backend, frontend, coverage, production-artifact, dependency-review, and secret-scan gates pass |
| III. UX and accessibility | PASS | Existing accessibility coverage remains passing and the browser shell behavior is unchanged |
| IV. Performance | PASS | Reproducible Chromium checks enforce LCP, INP, and CLS budgets; API read budgets remain passing |

There are no unresolved review conversations. If a second human receives repository write access,
the next production merge requires restoration of at least one independent approval.

## License decision

The repository owner selected the MIT License on 2026-10-01. The approved license text is included
in the repository-root `LICENSE` file.
