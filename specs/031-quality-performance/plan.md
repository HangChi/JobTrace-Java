# Plan

1. Extend component tests for account, admin, application, interview, reminder, import, and analytics success and failure paths. Re-run coverage after each group and retain the existing 80% gate.
2. Update browser tests to the current page labels and focus order, then run Playwright with mocked APIs.
3. Split page modules with React lazy loading and verify the production bundle and embedded JAR assets.
4. Run representative PostgreSQL query plans for application-list filters and deep pages. Change SQL only if measurements show a bottleneck and add a regression test for any resulting behavior change.
