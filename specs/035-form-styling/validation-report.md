# Validation Report

Local checks on 2026-10-27 (Asia/Shanghai):

- `npm run lint`: passed without warnings.
- `npm test`: 121 tests passed, including filter reset and URL synchronization regressions.
- `npm run build`: passed.
- `npm run test:coverage`: 95.87% lines and 80.99% branches overall; repository gates passed.
- `npm run test:performance`: five Chromium checks passed; sampled LCP 92 ms, INP 64 ms, CLS 0.
- Desktop (1440 px) and phone (390 px) browser screenshots were reviewed with mocked API data for Recruitment Market, My Applications, and the application editor. The dates no longer wrap vertically, and the phone layout has no horizontal table scroll.
- `git diff --check`: passed.

The Java backend, live account data, and remote database are unchanged. The screenshots used sample records only.
