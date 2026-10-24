# Data Model: Compatible Current-Session Logout

## Browser cookie

The configured ordinary or secure-prefixed `better-auth.session_token` carries an opaque token and HMAC signature. It is untrusted until verification with the shared secret. Missing, malformed, duplicated or invalid values never select a session. The `session_data` cache may occupy one cookie or numbered chunks; `dont_remember` may also be present. These are expired in the response but are never used for session selection.

## Session

The existing `sessions` row is identified by the verified opaque token. A single token-bound delete removes at most one row. Other rows, including another session for the same user, remain unchanged. There is no new table or migration.
