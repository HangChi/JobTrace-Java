# Quickstart: Account Email Unbinding

## Focused verification

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*EmailUnbinding*Test' test
```

Recorded 2026-10-04: 20 tests passed with no failures or skips.

## Repository verification

```bash
./mvnw verify
```

Recorded 2026-10-04: `BUILD SUCCESS`; 428 tests ran with 0 failures, 0 errors, and 1 intentional packaging skip. All 25 architecture tests, JaCoCo checks, Checkstyle, frontend build, and packaging passed. Changed production classes reached 95.3% line and 84.5% branch coverage. The representative 20-owner data set recorded p95 latency of 44 ms.

## Contract

Send `DELETE /api/profile/email` with a matching signed bridge assertion, trusted `X-JobTrace-Session-Id`, and `{ "currentPassword": "Current-Password" }`. Success returns `{ "email": null, "emailVerified": false }` and consumes only this owner's pending codes.

## Rollback

Retain legacy routing. If this slice is activated later and must be rolled back, route the exact DELETE path to the existing service. The data representation and schema do not change.
