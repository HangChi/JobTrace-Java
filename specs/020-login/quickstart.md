# Quickstart: Compatible Account Login

## Focused validation

```bash
./mvnw -Dtest='com.jobtrace.identityaccess.*Login*Test' test
```

## Whole-repository validation

```bash
./mvnw verify
```

## Scenario

With a registered account and its verified recovery email, send anonymous same-origin `POST /api/auth/login` using either username or email and the password. Expect HTTP 200, a safe `redirectTarget`, and an HttpOnly signed session cookie. The new `sessions` row must refer to that user and remain valid for seven days. Compare the cookie signature against the fixed Node/Better Auth vector; wrong credentials and disabled accounts must not add a session.

## Rollback

The old service keeps production routing. A future path-level rollback sends only this POST to the old service; the shared session schema remains unchanged.

## Local verification (2026-10-04)

`./mvnw verify -q` passed: 534 tests, zero failures or errors, one existing skip. The representative login latency assertion passed (five successful database-backed samples, p95 under one second). The fixed Node HMAC cookie vector, uniform credential denials, missing-secret fail-closed behavior, exact public route, rate limit and origin checks passed. JaCoCo reported 214 covered / 15 missed lines and 98 covered / 20 missed branches across changed identity-access production classes (93.4% line, 83.1% branch; the shared security configuration is excluded by project policy). Repository-wide coverage was 95.2% line and 81.8% branch. `git diff --check` passed. No deployment, CI/CD, or production traffic changes were made.
