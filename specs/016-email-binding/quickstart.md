# Quickstart: Account Email Binding

## Focused verification

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*EmailBinding*Test' test
```

## Whole-repository verification

```bash
./mvnw verify
```

Verified on 2026-10-04: focused email-binding tests passed; full local verification passed
with 450 tests, 0 failures, 0 errors, and 1 pre-existing skip. Changed 016 classes achieved
91.6% line coverage (175/191) and 80.8% branch coverage (63/78). Representative binding
write p95 was 48 ms across 20 fixtures.

## Contract

With a code already issued by the legacy service, send `PATCH /api/profile/email` with a PATCH-bound assertion, trusted current-session header, and `{ "email": "new@example.com", "verificationCode": "123456", "currentPassword": "Current-Password" }`. The Java process must receive `JOBTRACE_EMAIL_BINDING_VERIFICATION_SECRET` equal to the legacy `BETTER_AUTH_SECRET`. Success returns the lowercased address and `emailVerified: true`.

## Rollback

Legacy routing remains active. Later rollback routes only PATCH back to the existing service; the database representation and code format do not change.
