# Quickstart: Request Registration Email Code

## Focused verification

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*RegistrationCode*Test' test
```

## Whole-repository verification

```bash
./mvnw verify
```

## Local evidence (2026-10-04)

- Focused registration-code tests: passed.
- Whole-repository `./mvnw verify -q`: 492 tests, 0 failures, 0 errors, 1 skipped.
- Changed production classes: 97.1% line coverage (236/243), 92.3% branch coverage (96/104).
- Representative successful issuance p95: 1 ms, excluding external delivery latency.

## Contract

Configure `BETTER_AUTH_URL`, the stage 017 delivery endpoint and secret, and the shared verification HMAC secret. Send anonymous `POST /api/auth/email-code` with `{ "email": "new@example.com" }` and an allowed Origin. Expect HTTP 202 without the code; the delivery endpoint receives the six-digit registration code.

## Rollback

The old route stays active. A later rollback directs only this POST path back to the old service; stored code format and schema are shared.
