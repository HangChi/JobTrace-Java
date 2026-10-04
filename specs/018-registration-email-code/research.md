# Research: Request Registration Email Code

## Legacy endpoint and origin boundary

**Decision**: Preserve anonymous `POST /api/auth/email-code`, HTTP 202, and the ten-minute confirmation message. Reject `Sec-Fetch-Site: cross-site`; when Origin is present it must equal the configured legacy auth URL's origin. No Origin is accepted, matching the old route.

**Rationale**: The current route calls `assertSameOrigin` before parsing input. A bridge assertion cannot be required for prospective users. Java's default CSRF token requirement must be bypassed only for this route, which gets an explicit same-origin check.

## Purpose and representation

**Decision**: Store `registration` purpose, null user ID, six-digit code HMAC using `registration\0\0normalizedEmail\0code`, and ten-minute expiry; replacement consumes only prior unconsumed registration codes for the address.

**Rationale**: Matches `email-verification-service.ts` and preserves isolation from `email_binding` codes.

## Limits and conflict

**Decision**: Reuse the shared PostgreSQL limiter with source fallback `registration-email-code` (100/hour), normalized address (five/hour), and address cooldown (one/minute). Reject every address found in recovery_email with the old registration-specific field error.

**Rationale**: Matches the old service when proxy headers are not trusted; no client-supplied forwarding address is accepted.

## Delivery

**Decision**: Reuse stage 017's delivery port and HTTP adapter; require the same configured secret and endpoint.

**Rationale**: The mail template and payload are identical. No new delivery dependency or service is needed.

## Alternatives considered

- Requiring a signed bridge assertion would prevent anonymous registration.
- Broadly disabling CSRF would weaken unrelated routes.
- Sharing the authenticated binding-code store directly would conflate owner-scoped rows with null-user registration rows.
