# Feature 014: account password change

## Scope

Feature 014 adds only `POST /api/profile/password`. A verified account owner can prove the current password, replace it with an 8-to-16-character password, keep the verified current session, and revoke all other owner sessions.

Password reset, registration, login/logout, email binding, session issuance and refresh, schema ownership, frontend routing, deployment, production traffic, and CI/CD remain with the existing service.

## Contract and authorization

The exact POST path uses signed identity bridge v1 bound to method, path, and request ID. The verified subject is the only owner selector. Bounded `X-JobTrace-Session-Id` metadata selects the current session, whose active owner is independently checked before any mutation. Public identity headers and ordinary application principals do not authorize the command.

The request accepts `currentPassword` and `newPassword`; unrelated object properties are ignored. The replacement contains 8 through 16 characters. Wrong current passwords return `invalid_password` with a `currentPassword` field error. Success returns `密码已更新，其他设备已退出登录。`

## Persistence and compatibility

Java verifies and creates the same NFKC scrypt representation as Better Auth 1.6.27: a 16-byte lowercase hexadecimal salt, `N=16384`, `r=16`, `p=1`, and a 64-byte lowercase hexadecimal key separated by a colon. A fixed cross-runtime vector protects compatibility.

The database adapter first proves the owner-scoped active session and credential. One transaction then locks the current session, conditionally replaces the expected credential hash, and deletes only other sessions for that owner. Missing or stale state aborts the transaction. Foreign credentials and sessions are never selected or modified.

## Operations and rollback

Successful and failed responses use `Cache-Control: private, no-store`. Metrics contain only bounded outcome and latency labels. Account IDs, session IDs, passwords, hashes, assertions, request bodies, and SQL details are prohibited from diagnostics. The representative p95 budget is one second.

The legacy service remains the production owner. Routing rollback requires no schema or credential rollback because both runtimes accept the same stored password representation.
