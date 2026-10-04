# Data Model: Verified Account Registration

## Registration code

Existing `email_verification_codes` row: normalized email, purpose `registration`, null user ID, HMAC hash, expiry, consumed timestamp and attempt count. `verify_email_code_attempt` atomically increments failures or consumes a match. No new schema.

## User

Existing `users` row: generated text ID; lowercased username; display username; non-empty display name; internal email `<username>@users.jobtrace.local`; normalized recovery email; verified recovery timestamp; `email_verified=true`; ordinary `user` role. No session row is created.

## Credential

Existing `accounts` row: generated ID; `account_id=user.id`; `provider_id='credential'`; `user_id=user.id`; Better Auth-compatible scrypt password hash. User and credential insertions share one transaction. A uniqueness failure rolls back both.
