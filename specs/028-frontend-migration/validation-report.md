# Frontend migration validation — 2026-10-26

- `npm run lint`, `npm test` (108/108), `npm run test:coverage` and `npm run build` passed. Repository-wide coverage: lines 95.18%, branches 80.30%, functions 85.44%, statements 88.00%. The changed production code in this validation pass is limited to dependency resolution, so these global gates also exceed the changed-code floor.
- `npm run test:performance` passed five Chromium checks: authenticated application workflow, guest login/registration/recovery routes, protected deep-link redirect, ordinary-user administrator denial, keyboard navigation, and primary actions without horizontal overflow at 375, 768, 1024 and 1440 CSS pixels. The measured local Core Web Vitals sample was LCP 104 ms, INP 48 ms, CLS 0 against budgets of 2500 ms, 200 ms and 0.1.
- `App.a11y.test.tsx` uses axe-core on the login page and empty application workspace; both tests passed with the full unit suite. Its color-contrast rule is disabled, so this is not a claim of complete accessibility certification.
- `./mvnw verify` passed with Docker available: 744 tests, no failures or errors, one pre-existing skipped test. Maven embedded the current frontend build into the Spring Boot artifact. This run also passed JaCoCo and Checkstyle.

Browser tests use mocked API responses and do not replace the deferred live-account checks listed in `specs/029-code-hardening/quickstart.md`. Production traffic activation remains a separate release decision.
