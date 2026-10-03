# Validation Quickstart: Administration Read Model

Feature 009 is a local code migration slice. It does not deploy, route traffic, change the frontend, mutate access, manage sessions, administer the job market, or change the production schema.

## Focused checks

```bash
./mvnw -Dtest='com.jobtrace.admin.**' test
```

Verify administrator-only access, summary parity and independent failure, user filters/detail composition, audit filters/snapshots, safe validation, no-store responses, and zero writes.

## Full local gate

```bash
./mvnw verify
```

Expected: formatting, static analysis, architecture, unit/integration/contract/security/performance tests, and coverage all pass locally.

## Manual contract probes

Use signed one-time assertions bound to each exact method/path and the `X-Request-ID`. Confirm admin assertions return the established JSON, user assertions return 403, replay returns 401, and unsupported PATCH or job-market admin paths remain outside Java.
