# Research: Complete Account Authentication

## Browser session compatibility

**Decision**: Reuse the exact Better Auth signed cookie codec from 020/021, then verify its token against `sessions` joined to current `users` on every protected browser request. Do not trust a cookie signature alone. Use the current database role and disabled state, not stale cookie claims.

**Rationale**: A signed but revoked cookie must lose access immediately; the old service and Java share the same session table.

**Alternatives**: Trust the signed token without a database read (reject: revocation and disablement would be delayed); reinterpret the cookie as a signed bridge assertion (reject: mixes trust boundaries).

## Bridge coexistence and browser writes

**Decision**: Keep the existing short-lived, replay-protected bridge assertion route. Browser identity is a distinct verified principal. Reject state-changing cookie-authenticated requests without exact trusted origin evidence; keep auth bootstrap routes on their existing origin guard. Never use `X-JobTrace-Session-Id` or any public identity header to prove browser identity.

**Rationale**: Browser cookies are ambient credentials, while bridge assertions are explicit server-to-server credentials.

**Alternatives**: Allow all signed-cookie writes without origin checking (reject: CSRF); make the bridge token available to browser code (reject: leaks a server credential).

## Password-reset compatibility

**Decision**: Preserve the installed Better Auth format `reset-password:<24-character token>` in `verification_tokens` with one-hour expiry. Request responses are identical for known and unknown recovery addresses; verified recovery email is the only delivery target. Consume the token and update the credential/revoke sessions in one transaction. Keep the legacy callback-link shape when sending mail.

**Rationale**: Existing links and sessions remain meaningful during migration; atomic use prevents two resets from winning the same token.

**Alternatives**: Introduce a new token table (reject: schema ownership is unchanged); delete the token before password hashing/transaction (reject: failure would strand a valid link).

## Avatar compatibility

**Decision**: Enforce the existing 5 MiB limit, supported MIME types and magic-byte checks before upload. Store under an owner-scoped random COS object key, return the public URL, and leave profile mutation to the existing profile endpoint.

**Rationale**: Matches the old contract without creating a new avatar persistence model.

**Alternatives**: Store image bytes in PostgreSQL (reject: incompatible and unnecessary).
