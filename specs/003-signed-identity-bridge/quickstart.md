# Quickstart: Signed Identity Bridge and Analytics Activation

This validates the feature without activating production traffic.

## Prerequisites

- Java 21, Docker, Node.js 24, and npm
- Java repository plus sibling legacy JobTrace repository
- Synthetic PostgreSQL fixtures and disposable Valkey/Redis-compatible storage
- Two generated test secrets of at least 32 random bytes supplied through environment variables

Never use production sessions, identities, dumps, or keys.

## 1. Verify Java behavior

```bash
./mvnw -Dtest='*Bridge*,*AnalyticsSummarySecurity*,ContractComparisonTest,MigrationSliceTest' test
./mvnw verify
```

Expected: valid first use authenticates; malformed, forged, expired, premature, misaddressed,
request-mismatched, unknown-key, and replayed assertions return 401; owner isolation, contract
comparison, Checkstyle, and coverage pass.

## 2. Verify the legacy issuer

From the legacy repository:

```bash
npm test -- --run identity-bridge
npm run lint
npm test
npm run build
```

Expected: issuer requires an authenticated enabled account, reads current role/access version, emits
the exact v1 contract, and never returns or logs the token to a browser.

## 3. Verify cross-service compatibility

Start disposable PostgreSQL and Valkey, then start Java with the bridge disabled by default. Supply
issuer, audience, accepted key IDs, secret references, and replay-store connection only in the test
environment. Enable the bridge and send one internally generated synthetic request.

Expected: first request matches normalized legacy JSON; reuse returns 401; changing request ID,
method, path, key ID, or signature returns 401; logs contain no token, user ID, or response body; and
disabling bridge routing returns ownership to legacy without a database change.

## 4. Rehearse rotation and rollback

Add a new accepted key, switch the issuer, validate both during overlap, wait longer than 35 seconds,
remove the previous key, and verify its rejection. Then follow
`docs/migration-slices/analytics-summary.md` with synthetic canary traffic and confirm 100% legacy
routing can be restored within five minutes.

Production activation additionally requires CI, written Solo-Maintainer Mode self-review, approved
deployment configuration, and seven consecutive clean observation days.
