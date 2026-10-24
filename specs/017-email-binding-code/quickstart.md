# Quickstart: Request Email Binding Code

## Focused verification

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*EmailCode*Test' test
```

## Whole-repository verification

```bash
./mvnw verify
```

Verified locally on 2026-10-04: focused tests and whole-repository verification passed.
The complete run reported 470 tests, 0 failures, 0 errors, and 1 existing skip.
Changed 017 classes achieved 93.8% line coverage (198/211) and 86.0% branch coverage
(86/100). Representative issuance p95 was 1 ms across 20 fixtures, excluding remote
delivery latency.

## Contract

Configure `JOBTRACE_EMAIL_BINDING_VERIFICATION_SECRET` with the same secret as the existing service, plus the existing email-delivery URL and optional delivery secret. Send `POST /api/profile/email/code` with a POST-bound assertion, trusted current-session header, and `{ "email": "new@example.com" }`. Expect 202 with a safe confirmation; the delivery endpoint receives the six-digit code. Redeem it through stage 016's PATCH route.

## Rollback

Legacy routing remains active. Later rollback directs only this POST route back to the existing service; schema and code format are shared.
