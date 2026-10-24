# Data Model: Compatible Account Login

## User and credential

Existing `users` row supplies ID, username, role, disabled flag, normalized recovery email and verified timestamp. Existing `accounts` credential row supplies the Better Auth scrypt hash for `provider_id='credential'`. Email lookup is allowed only when the recovery email is verified and the account is enabled. A username lookup may return a disabled user but never issues a session.

## Session

Existing `sessions` row stores generated text ID, unpredictable raw token, user ID, creation and update times, seven-day expiry and bounded user-agent. The raw token is never returned in JSON or metrics. The cookie carries the token plus HMAC signature for the old service to verify. A failed login inserts no session.
