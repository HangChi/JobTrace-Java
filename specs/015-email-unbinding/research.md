# Research: Account Email Unbinding

## Existing behavior

The legacy `DELETE /api/profile/email` requires an active user and same-origin request, consumes the `email-change` limit (5 per 15 minutes), verifies the current password, then updates only that user's recovery email fields and consumes their pending verification codes in one transaction. It returns `{ "email": null, "emailVerified": false }`.

## Decisions

- The signed bridge replaces the legacy cookie and same-origin trust boundary for this Java route. The assertion is bound to DELETE, exact path, and request ID; Java also checks the trusted current-session ID against the owner and expiry.
- Reuse the feature 014 credential read and scrypt verifier. This avoids introducing another password representation or verification policy.
- Call the existing `consume_auth_rate_limit` function with SHA-256 of the owner ID, action `email-change`, limit 5, and window 900 seconds. This shares the legacy limiter's state.
- In one transaction, lock the current session, ensure the credential hash still matches the verified snapshot, clear the owner's email state, and consume only that owner's pending codes. A missing owner or changed credential fails the transaction safely.
- Keep the legacy endpoint as production owner. Activation and routing are separate work.
