# Validation: Frontend Migration

## Commands

From `frontend/`, run `npm run lint`, `npm test`, `npm run build` and `npm run test:coverage`. For backend contract changes, run `./mvnw verify` from the Java repository. Use the existing Playwright performance configuration for representative route checks.

## Scenarios

1. Open `/applications` anonymously. Expect a redirect to `/login` with an internal `returnTo`; reject external destinations.
2. Login, refresh `/applications`, sign out and revisit the deep link. Expect Java cookie/session behavior without browser token storage.
3. Create, edit and filter an application, manage its stages and reminder, then inspect analytics and export.
4. Preview a mixed CSV import and confirm only selected valid rows; show each outcome.
5. Manage a private interview and engage with a public one; update profile settings.
6. Enter `/admin` as admin and as user; verify role gate, live supported data and an explicit 027-dependent unavailable state.
7. Inspect 375/768/1024/1440 px layouts, keyboard-only navigation, focus visibility, labels, live feedback, reduced motion and no horizontal scroll.

Record coverage, test counts and measured web vitals before marking the stage implemented. No production cutover or CI/CD change is included.
