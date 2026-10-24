# Data Model: Request Registration Email Code

| Existing entity | Relevant fields | Rule |
| --- | --- | --- |
| User | `id`, `recovery_email` | Any matching recovery email conflicts. |
| Verification code | `id`, `email`, `purpose`, `user_id`, `code_hash`, `expires_at`, `consumed_at`, `attempt_count` | Purpose is `registration`, user ID null, expiry ten minutes, initial attempts zero. |
| Rate limit | `key_hash`, `action`, `window_started_at`, `request_count` | Source, address and cooldown counters use existing actions. |

On success, previous unconsumed registration codes for the normalized address are consumed and one new code is inserted in one transaction. Binding-purpose codes remain untouched. Delivery follows durable insertion.
