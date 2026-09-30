# JobTrace Java Context

JobTrace Java is the target repository for migrating the existing JobTrace modular monolith from a Next.js server runtime to Spring Boot while retaining React and TypeScript in the browser.

The existing `JobTrace` repository is the behavioral source of truth during migration. Its PostgreSQL schema, stored functions, API behavior, authorization outcomes, owner isolation, optimistic locking, audit requirements, and user experience must be preserved unless a new specification explicitly changes them.

## Bounded contexts

- `applications`: private job applications, stages, events, and optimistic concurrency.
- `interviews`: interview reviews, autosave, public sharing, questions, and actions.
- `analytics`: summaries, reminders, and period reports.
- `reminders`: in-app reminders and delivery orchestration.
- `datatransfer`: spreadsheet preview, import confirmation, and exports.
- `jobmarket`: public company and job-source discovery, synchronization, favorites, and tracking links.
- `identityaccess`: authentication, profiles, roles, sessions, and administrative audit.
- `shared`: database, HTTP errors, dates, observability, and cross-cutting security.

## Migration invariants

- One writer per business aggregate.
- Legacy database migrations are never edited or replayed blindly.
- Cross-owner identifiers remain indistinguishable from missing records.
- Public clients cannot assert identity or role through headers.
- Authentication migrates only after a dedicated compatibility specification.
- Each slice has contract evidence, an observation window, and a rollback route.

