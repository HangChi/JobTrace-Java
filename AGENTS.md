# Agent instructions

## Issue tracker

Requirements and tasks are managed through the speckit workflow under `specs/<NNN>-<slug>/` using `spec.md`, `plan.md`, and `tasks.md`.

## Architecture

- Keep the application a modular monolith unless an approved specification justifies another deployment unit.
- Preserve the dependency direction `web -> application -> domain`; infrastructure implements application ports.
- Use jOOQ or Spring JDBC for the existing PostgreSQL schema. Do not introduce JPA mappings as a mechanical replacement for established SQL routines.
- Keep React and TypeScript in `frontend/`. Production builds must embed compiled assets in the Spring Boot artifact.
- Do not enable Flyway against a legacy JobTrace database until the schema baseline task is complete and approved.
- Never trust public identity headers. Authentication bridging requires a separately reviewed signed server-to-server contract.

## Quality gates

- Run `./gradlew test` for backend changes.
- Run `npm run lint`, `npm test`, and `npm run build` from `frontend/` for frontend changes.
- Add tests for every behavior change and preserve at least 80% line and branch coverage for changed production code.
- Never commit secrets, database dumps, local environment files, generated build output, or frontend dependencies.

