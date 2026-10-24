# Quickstart: Account Password Change

## Focused validation

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Password*Test' test
```

Expected: validation, Better Auth compatibility, owner/session isolation, response parity, authorization, atomicity, failure, metrics, and performance tests pass.

Recorded 2026-10-03: 23 tests passed with no failures or skips. The representative 20-change data set recorded p95 latency of 87 ms.

## Whole-repository validation

```bash
./mvnw verify
```

Expected: all tests, coverage, architecture, Checkstyle, frontend build, and packaging checks pass.

Recorded 2026-10-03: `BUILD SUCCESS`; 408 tests ran with 0 failures, 0 errors, and 1 intentional packaging skip. All 25 architecture tests, JaCoCo coverage checks, Checkstyle, frontend build, and packaging passed.

## Manual contract

Send `POST /api/profile/password` with a POST-bound assertion, trusted `X-JobTrace-Session-Id`, and `{ "currentPassword": "Current-Password", "newPassword": "New-Password" }`. Expect `{ "message": "密码已更新，其他设备已退出登录。" }`; the supplied owner session remains and other owner sessions are gone.

## Rollback

Keep the legacy POST as production owner. If later activation fails, route the password path back to the legacy implementation; compatible hashes need no data rollback and no schema rollback is necessary.
