# Dependency advisory cleanup

**Status**: Complete — merged in PR #24 on 2026-10-26; GitHub shows zero open Dependabot alerts

## Goal

Resolve the seven open Dependabot advisories affecting frontend development dependencies without changing application behavior.

## Requirements

- Update the locked Vite and undici dependency graph to patched versions through npm's normal resolver.
- Keep the application and API contracts unchanged.
- Verify `npm audit` reports no vulnerabilities and run frontend lint, unit, coverage, build and browser checks.
- Run the backend verification gate because the Spring Boot artifact embeds the frontend build.

## Acceptance

- The lockfile resolves Vite outside the affected `8.3.0`–`8.3.2` range and undici outside the affected `<8.12.2` range.
- Local npm audit, frontend checks and `./mvnw verify` pass.
- GitHub Dependabot alerts close after the verified change reaches the default branch.
