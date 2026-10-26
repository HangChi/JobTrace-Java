# Tasks: Frontend Migration

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/routes.md](contracts/routes.md)

## Phase 1: Shared foundation

- [x] T001 Add same-origin JSON client, typed ProblemDetail parsing and tests in `frontend/src/shared/api/`.
- [x] T002 Add safe browser route mapping, history navigation and tests in `frontend/src/app/`.
- [x] T003 Replace migration-only styling with accessible mint workspace tokens and responsive shell in `frontend/src/index.css` and `frontend/src/App.css`.

## Phase 2: User Story 1 — Enter and navigate the workspace

**Independent test**: Anonymous protected deep link, login success/failure, refresh, recovery, registration and sign-out with mocked Java responses.

- [x] T004 [US1] Add auth session and command contract tests in `frontend/src/features/auth/`.
- [x] T005 [US1] Implement login, registration and recovery forms backed by Java auth endpoints in `frontend/src/features/auth/`.
- [x] T006 [US1] Implement session bootstrapping, protected workspace navigation, role gate and sign-out in `frontend/src/App.tsx` and `frontend/src/app/`.
- [x] T007 [US1] Replace placeholder tests with auth/route/a11y coverage in `frontend/src/App.test.tsx` and `frontend/src/App.a11y.test.tsx`.

## Phase 3: User Story 2 — Applications and reminders

**Independent test**: Application create/edit/stage/delete and reminder lifecycle against mocked Java responses, plus analytics and import preview/confirm.

- [x] T008 [US2] Add Java application and reminder contract/adaptor tests in `frontend/src/features/applications/` and `frontend/src/features/reminders/`.
- [x] T009 [US2] Implement application list, filters, detail, form, status/stage actions and deletion.
- [x] T010 [US2] Implement reminder list, create/edit, complete/reopen/postpone/retry and settings.
- [x] T011 [US2] Implement analytics/report/progress views and export in `frontend/src/features/analytics/`.
- [x] T012 [US2] Implement CSV/XLSX import preview and selected-row confirmation in `frontend/src/features/import/`.

## Phase 4: User Story 3 — Interviews and profile

**Independent test**: Private interview mutation, public engagement and account update with mocked Java responses.

- [x] T013 [US3] Add interview and profile contract/adaptor tests.
- [x] T014 [US3] Implement private interview CRUD, public list/detail/comment/like and privacy states in `frontend/src/features/interviews/`.
- [x] T015 [US3] Implement profile and supported account/security settings in `frontend/src/features/profile/`.

## Phase 5: User Story 4 — Administration

**Independent test**: Admin and non-admin direct navigation; summary, users and audit states, supported changes and unavailable marketplace operations.

- [x] T016 [US4] Add admin contract/role-gate tests in `frontend/src/features/admin/`.
- [x] T017 [US4] Implement Java-supported admin summary, users, audit and account actions.
- [x] T018 [US4] Render shared marketplace catalog, source, review and run data, with an explicit legacy-owned ingestion state and no Java crawl action.

## Phase 6: Validation

- [x] T019 Run lint, unit, coverage and production build; meet changed-code 80% line/branch coverage.
- [x] T020 Verify representative browser flows, responsive widths, keyboard/a11y and performance budgets; update spec/checklist only after evidence.
