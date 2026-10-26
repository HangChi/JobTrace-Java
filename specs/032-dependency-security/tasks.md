# Tasks

- [x] Identify affected frontend dependencies and patched version ranges.
- [x] Update `frontend/package-lock.json` with npm's security fix.
- [x] Run npm audit, frontend lint, unit, coverage, build and browser checks.
- [x] Run Docker-backed `./mvnw verify` with the updated frontend build.
- [ ] Merge the verified lockfile through PR checks and confirm the default branch's alerts close.
