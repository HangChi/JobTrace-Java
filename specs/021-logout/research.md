# Research: Compatible Current-Session Logout

## Legacy behavior

**Decision**: Preserve same-origin checking followed by Better Auth sign-out, then an empty 204 response.

**Rationale**: The old `src/app/api/auth/logout/route.ts` delegates to `auth.api.signOut`; the installed `dist/api/routes/sign-out.mjs` verifies the signed session cookie, deletes that token best-effort, expires authentication cookies and returns success even if no session exists or deletion fails.

**Alternatives considered**: Requiring a signed bridge assertion for browser logout or revoking every user session; both differ from the established one-browser flow.

## Cookie verification and cleanup

**Decision**: Extend the feature-020 cookie component to decode and verify the installed Better Auth `token.HMAC-SHA256(token)` representation with constant-time comparison, an exact configured cookie name, and bounded input length. Expire session-token, cached-session (including chunks), and remember-state cookies with legacy attributes.

**Rationale**: `dist/cookies/index.mjs` uses the configured secure prefix, `Path=/`, `HttpOnly`, `SameSite=Lax` and `maxAge: 0` for expiry. The parser must reject duplicate target names to avoid ambiguous deletion.

**Alternatives considered**: Trusting a raw token, trusting a public identity header, or deleting by user ID; these would permit cross-session or cross-user revocation.

## Persistence failure

**Decision**: Preserve best-effort 204 with browser cookie expiry and a bounded `delete_failure` metric if the database operation fails.

**Rationale**: This matches the old service. The session might remain until expiry, so the outcome must be observable without leaking token values.
