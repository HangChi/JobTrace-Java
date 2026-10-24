# Data Model: Account Email Unbinding

## Existing rows

- `users`: `id` owner key; nullable `recovery_email`, nullable `recovery_email_verified_at`, boolean `email_verified`; `updated_at` changes on success. `email`, username, image, and profile name remain unchanged.
- `accounts`: `user_id`, `provider_id='credential'`, and `password` identify the verified current credential. No account row is changed.
- `sessions`: `id`, `user_id`, `expires_at` prove that the supplied current session is active and belongs to the owner. No session row is changed.
- `email_verification_codes`: `user_id` and nullable `consumed_at` identify pending owner codes. All unconsumed owner rows receive `consumed_at=now()` in the same transaction as the profile update.
- `auth_rate_limits`: Existing function keys attempts by SHA-256 owner ID and `email-change`. Five requests are allowed in a 900-second window.

## Invariants

- At most one owner row is updated; no foreign owner or code rows change.
- Credential verification and current-session ownership are prerequisites for mutation.
- If the user update, session lock, credential check, or code update fails, the transaction rolls back.
- Repeated unbinding is idempotent in the email response and state, while each request still consumes the rate-limit attempt.
