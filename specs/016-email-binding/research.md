# Research: Account Email Binding

## Legacy contract

`PATCH /api/profile/email` consumes `email-change` (5 per 15 minutes), verifies the current password, validates a lowercased email and trimmed six-digit code, invokes `verify_email_code_attempt` for purpose `email_binding` and owner, then updates recovery email and verified status. It returns `{email, emailVerified:true}`. A unique recovery-email index enforces conflicts.

## Compatibility decisions

- Use the same HMAC-SHA256 input sequence: `email_binding`, NUL, owner ID, NUL, lowercased email, NUL, trimmed code. The UTF-8 secret is the legacy `BETTER_AUTH_SECRET` supplied to Java through `JOBTRACE_EMAIL_BINDING_VERIFICATION_SECRET`.
- Preserve the existing database function's serialization, expiry, replay, and five-attempt behavior. Call it inside a transaction; return a wrong-code result so the increment commits. On a match, update the owner in the same transaction so update failures roll back code consumption.
- Recheck active owner session and exact credential hash inside that transaction after password verification. A concurrent credential rotation or session expiry fails closed.
- Use the same shared email-change limiter introduced in 015. The legacy code-issuance route remains the producer and sole mail-delivery owner.
- Do not write or log the raw code, password, HMAC secret, credential hash, email address, or owner ID in diagnostics.
