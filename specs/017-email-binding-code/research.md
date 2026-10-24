# Research: Request Email Binding Code

## Legacy contract

**Decision**: Preserve `POST /api/profile/email/code`, returning HTTP 202 and `{ "message": "验证码已发送，10 分钟内有效。" }` on success.

**Rationale**: The legacy route delegates to `requestEmailBindingCode` after same-origin checking. The Java bridge independently authenticates exact method and path. No code is returned.

## Code representation and replacement

**Decision**: Generate a six-digit random code, store only `HMAC-SHA256(secret, "email_binding\0owner\0email\0code")`, expire at ten minutes, and consume previous unconsumed codes for the same owner/address/purpose in one transaction.

**Rationale**: Matches `email-verification-service.ts` and is accepted by the stage 016 verifier. A test-only fixed code bypass is excluded from production Java code.

## Limits and conflict

**Decision**: Reuse `consume_auth_rate_limit` with SHA-256 keys: source fallback `binding-email-code` at 100/hour, normalized address at five/hour and one/minute. Reject a recovery email owned by another user.

**Rationale**: Matches the legacy default when proxy headers are not trusted. Java does not trust client-provided forwarding headers. Limit consumption occurs outside code insertion so rejected attempts remain counted.

## Mail delivery

**Decision**: Use the existing HTTP endpoint with JSON `to`, `template: email_verification_code`, `code`, and `expiresInSeconds: 600`; optional Bearer secret, ten-second timeout. Map 422 `invalid_recipient` distinctly; other failures are safe delivery errors.

**Rationale**: No new mail dependency or template system is needed. The code is persisted before delivery, matching legacy behavior; a delivery failure leaves an unusable-to-the-user code until replacement/expiry, documented as a limitation.

## Alternatives considered

- Sending mail before persistence risks delivering a code that cannot be redeemed.
- An outbox would require schema and delivery-worker scope beyond this stage.
- Trusting `X-Forwarded-For` would allow client spoofing without a separately configured trusted proxy boundary.
