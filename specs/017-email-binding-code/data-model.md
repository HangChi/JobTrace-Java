# Data Model: Request Email Binding Code

## Existing records

| Entity | Relevant fields | Rule |
| --- | --- | --- |
| Owner session | `id`, `user_id`, `expires_at` | Must be active and owned by assertion subject. |
| User | `id`, `recovery_email` | A target email belonging to another user conflicts. |
| Verification code | `id`, `email`, `purpose`, `user_id`, `code_hash`, `expires_at`, `consumed_at`, `attempt_count` | New row has purpose `email_binding`, ten-minute expiry, zero attempts, and no consumption. |
| Auth rate limit | `key_hash`, `action`, `window_started_at`, `request_count` | Existing function updates source, address, and cooldown counters. |

## Transition

On valid request, consume earlier active rows for the same normalized address, purpose, and owner; insert one new hashed code. A code for a different owner, address, or purpose is untouched. Delivery occurs after the transaction commits.
