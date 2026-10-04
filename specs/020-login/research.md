# Research: Compatible Account Login

## Legacy login sequence

**Decision**: Preserve origin check, ten-per-minute `login` source limit, optional challenge, validation, username/verified-email lookup, credential verification and role-based redirect.

**Rationale**: The old `POST /api/auth/login` calls these in order. Errors for absent, wrong or disabled accounts are deliberately identical.

**Alternatives considered**: Accepting unsigned identity headers or browser cookies directly as Java authorization; rejected by the existing bridge invariant.

## Session and cookie interoperability

**Decision**: Use the installed Better Auth 1.6 session shape: DB token with seven-day expiry, cookie name `better-auth.session_token` or secure-prefixed variant, HMAC-SHA256 signature encoded as standard Base64, and percent-encoded signed value. Cookie attributes are `HttpOnly`, `SameSite=Lax`, `Path=/`, seven-day max age, and `Secure` when required.

**Rationale**: These details were inspected in the installed package's `dist/db/internal-adapter.mjs`, `dist/cookies/index.mjs`, `dist/crypto/index.mjs`, and its installed `better-call` cookie serializer. The legacy service can then read the same session row and verify the signature.

**Alternatives considered**: Inventing a Java-only session cookie or calling legacy over HTTP for every login; both defeat the migration or create a second incompatible session system.

## Credential denial and timing

**Decision**: Use the existing Better Auth-compatible scrypt verifier and perform equivalent hash work for an unknown username; deny disabled users without creating a session.

**Rationale**: This preserves the generic `invalid_credentials` public response and avoids a cheap account-existence timing signal.

**Alternatives considered**: Distinct absent/disabled errors or plaintext comparison; both violate security requirements.

## Shared challenge boundary

**Decision**: Reuse the 019 challenge port/adapter for login, preserving the configured five-second external verification and fail-closed behavior. The existing class names stay stable to avoid unnecessary changes to registration.

**Rationale**: Both public flows use the same legacy challenge contract. Keeping separate copies would duplicate a security rule.
