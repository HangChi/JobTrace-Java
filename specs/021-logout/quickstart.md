# Quickstart: Compatible Current-Session Logout

## Focused validation

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Logout*Test' test
```

## Whole-repository validation

```bash
./mvnw verify
```

## Scenario

Sign in twice using separate browser sessions. POST `/api/auth/logout` with one signed cookie and matching Origin. Expect empty 204, expired session-token/cache/remember cookies, one deleted database row and the other session intact. Repeat with the old cookie, no cookie, a tampered cookie and a cross-site Origin; none may delete the other session. Verify the fixed Better Auth signature vector and representative p95 below one second.

## Rollback

The old service keeps production routing. A future path-level rollback sends only this POST to the old service; the shared sessions schema remains unchanged.

## Local verification (2026-10-04)

`./mvnw verify -q` passed: 546 tests, zero failures or errors, one existing skip. The representative five-request logout p95 assertion passed below one second. The compatibility and security tests covered signature verification, secure/plain names, missing/tampered/duplicate/stale cookies, cached-session chunks, single-row isolation, same-origin rejection, unsigned headers, missing secret and best-effort database failure. JaCoCo reported 135 covered / 5 missed lines and 78 covered / 16 missed branches across changed identity-access production classes (96.4% line, 83.0% branch; shared security configuration is excluded by project policy). Repository-wide coverage was 95.2% line and 81.7% branch. No deployment, CI/CD or production traffic changes were made.
