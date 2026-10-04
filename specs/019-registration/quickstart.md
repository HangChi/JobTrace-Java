# Quickstart: Verified Account Registration

## Focused validation

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Registration*Test' test
```

## Whole-repository validation

```bash
./mvnw verify
```

## Local evidence (2026-10-04)

- Focused 019 registration and 018 registration-code tests: passed.
- Whole-repository `./mvnw verify -q`: 519 tests, 0 failures, 0 errors, 1 skipped.
- Changed production classes: 93.9% line coverage (260/277), 86.3% branch coverage (113/131).
- Representative successful registration write p95: 58 ms, excluding external challenge latency.

## Scenario

Use the existing registration-code request to issue a code for an unclaimed email, then anonymously POST valid username, email, verificationCode and password to `/api/auth/register` with an allowed Origin. Expect HTTP 202 and no session. Inspect the user, credential and consumed code; verify the stored password using the existing scrypt adapter. Retry with the same code and expect `invalid_email_code`.

## Rollback

The old service keeps production routing. A future path-level rollback sends only this POST to the old service; no schema or data migration is required.
