# Implementation Plan: Frontend Migration

**Branch**: `codex/028-frontend` | **Date**: 2026-10-08 | **Spec**: [spec.md](spec.md)

## Summary

Replace the Vite health shell with the legacy JobTrace workflows backed by same-origin Java APIs. Build a small shared API client and route-aware React shell, then migrate auth, applications/reminders/analytics/import, interviews/profile, and Java-supported administration. Read shared marketplace data through completed 027 APIs; crawling remains exclusively legacy-owned.

## Technical Context

**Language/Version**: TypeScript 6, React 19, Vite 8.

**Primary Dependencies**: Existing React, React DOM, browser Fetch/History APIs; avoid new router or UI package unless justified by implementation evidence.

**Storage**: No browser session-token storage; Java signed cookie and APIs hold state. URL search parameters hold filter/page state.

**Testing**: Vitest, Testing Library, axe-core, Playwright performance checks; `npm run lint`, `npm test`, `npm run build`, coverage.

**Target Platform**: Responsive same-origin browser UI embedded in Spring Boot artifact.

**Project Type**: Existing frontend workspace of modular Java monolith.

**Performance Goals**: LCP <=2.5 s p75, INP <=200 ms p75, CLS <=0.1 on representative lab routes; avoid large all-at-once page bundles.

**Constraints**: No public identity headers, no local/session storage for auth, safe return navigation, familiar terminology, WCAG 2.2 AA, 80% changed-code line/branch coverage. No CI/CD or production route edits.

**Scale/Scope**: Eight user/admin page groups plus auth and shared shell, staged as independently testable workflows.

## Constitution Check

- Maintainability: shared API/problem handling and small route modules, no copy-pasted fetch/error logic.
- Testing: route, form and API-contract tests before each page group; keyboard/a11y checks and quality commands.
- Consistency/accessibility: preserve old mint JobTrace visual language and interaction terms; visible focus, labels, status messages and responsive layout.
- Performance: split page groups and reserve loading space; measure representative routes before completion.

No exception planned. Rechecked after design: Java session cookie, same-origin API and SPA forwarding support this plan without a new dependency or deployment unit.

## Visual Direction

The subject is a job-search workbench for people tracking applications. Preserve the old soft-mint palette (`#f4fbf9` paper, `#ffffff` surface, `#163d3a` ink, `#087f73` action, `#d7ebe6` line, `#b4233a` danger), with system Chinese body type and a restrained tabular utility face for dates/counts. Layout uses a compact desktop sidebar and a small-screen top/scrolling navigation; the application stage rail is the single recognizable signature, encoding actual progress instead of decoration. No gratuitous motion; status changes are announced and reduced-motion is respected.

The generic blue landing-page recommendation from the UI search was rejected because this is an existing workspace, not a marketing funnel. Existing JobTrace vocabulary and brand tokens win.

## Project Structure

```text
specs/028-frontend-migration/
  spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
frontend/src/
  app/                 # route, auth and workspace shell
  features/            # auth, applications, reminders, analytics, import,
                       # interviews, profile and admin page groups
  shared/api/          # same-origin client and typed contracts
  shared/ui/           # forms, feedback, navigation and visual tokens
```

**Structure Decision**: Stay in `frontend/` so the existing Maven/Vite build embeds assets. Keep API adapters separate from presentational components. Use native browser navigation until complexity warrants a dependency.

## Delivery Sequence

1. Shared API/session/router shell and login/register/recovery.
2. Application and reminder workflows, analytics and import/export.
3. Private/public interviews and profile/account settings.
4. Java-supported admin pages, contract/a11y/performance validation.

Java reads shared 027 marketplace data and supports curation operations, but does not run ATS or WeChat crawling, invoke legacy ingestion, or proxy to the old frontend.
