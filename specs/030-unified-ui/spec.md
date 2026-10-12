# Unified JobTrace UI

## Goal

Bring every React page close to the public JobTrace site's page structure, controls and spacing. Keep existing routes, workflows, and data contracts intact.

## Requirements

- Apply consistent typography, color, spacing, surfaces, controls, focus states, and feedback across guest, user, and admin pages.
- Match the reference page composition: authentication form geometry, hero actions, joined metric strips, filter bars, dense tables, public interview sidebar, import steps, profile settings layout, and admin top navigation.
- Keep local-only workflows accessible when the reference lacks a corresponding route.
- Keep navigation and data dense pages usable at narrow phone widths without horizontal page overflow.
- Preserve readable labels and keyboard focus. Respect reduced motion settings.
- Do not change the database schema, production data, or server configuration.

## Acceptance

- Login, registration, password recovery, job market, applications, interviews, analytics, reminders, import, profile, and admin pages share the same control and surface language.
- Frontend lint, tests, and production build pass.
- The page must not overflow the viewport horizontally at 390px width.
