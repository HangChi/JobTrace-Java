# Research: Frontend Migration

## Baseline and contract

**Decision**: The old Next.js pages are a behavioral and visual reference; Java controllers and their contract tests are the API authority for this branch.

**Rationale**: The old UI serves users today, while Java endpoint details (cookie, errors and response shape) differ in places. Blindly copying server components or Next.js actions would not work in Vite.

**Alternative considered**: Copy the old page code wholesale; rejected because it depends on Next.js server components, actions and routing.

## Authentication

**Decision**: Use `/api/auth/session`, `/api/auth/login` and `/api/auth/logout` with same-origin credentials. Keep only the user view in React memory and revalidate on protected loads. Validate `returnTo` as an internal path.

**Rationale**: Java owns the signed browser cookie and trusted identity bridge. Browser storage and identity headers would create a second, unsafe auth path.

**Alternative considered**: Cache tokens locally or send `x-user-id`; rejected by repository security rules.

## Routing and dependency

**Decision**: Use browser URL/History with a small route table for the initial migration, preserving direct links and back/forward. Expose unavailable 027 admin operations as disabled, not as silent legacy fallbacks.

**Rationale**: Spring's SPA forward already supports browser routes, and the current frontend has no router dependency. A new package is not needed for the initial page set.

**Alternative considered**: Add React Router immediately or proxy old endpoints; rejected due extra maintenance and ownership ambiguity.

## Interaction and visual system

**Decision**: Reuse the old mint JobTrace palette, terms and status labels; build explicit loading/empty/error/validation/success states with semantic controls and a compact stage-progress rail.

**Rationale**: This is a migration for existing users, not a redesign. The UI/UX skill search returned a generic blue funnel recommendation inconsistent with the workspace, so it was not adopted. The React stack search returned no verified match; native React/browser patterns and the skill's accessibility checklist guide implementation.

**Alternative considered**: Marketing-style hero and general dashboard template; rejected because they obscure task actions and change familiar visual language.
