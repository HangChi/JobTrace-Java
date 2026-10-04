# Data Model: Account Email Binding

- `users`: owner ID, nullable recovery email, nullable verified timestamp, email-verified flag, updated timestamp. Only the verified owner's recovery fields change on success. A case-insensitive unique index prevents duplicate recovery addresses.
- `accounts`: existing owner credential and password hash. Read for verification and locked for a conditional recheck; never mutated.
- `sessions`: current active owner session. Read and locked; never mutated.
- `email_verification_codes`: existing owner/address/purpose-bound HMAC hash, expiry, attempt count, consumed timestamp. The legacy function increments a wrong active attempt or consumes a match.
- `auth_rate_limits`: existing shared `email-change` row. A binding request consumes one attempt before password verification.

## State transitions

- Valid code + successful profile update: code becomes consumed and owner email becomes verified in one commit.
- Wrong active code: attempt count increments in one commit; profile remains unchanged.
- Expired, consumed, exhausted, foreign-owner, or foreign-email code: no profile mutation.
- Unique-email conflict or dependency failure: transaction rolls back a valid code's consumption and profile update.
