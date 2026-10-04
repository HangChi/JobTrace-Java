# Account Email Unbinding (Feature 015)

This slice adds the authenticated `DELETE /api/profile/email` command. A signed bridge assertion must match the exact method, path, and request ID. A trusted current-session ID must identify an active session owned by the verified subject. The owner also supplies the current password, verified against the existing Better Auth-compatible credential.

The command shares the existing `email-change` limit of five attempts per fifteen minutes. On success, one transaction clears the owner's recovery email, verified timestamp, and verification flag, then consumes that owner's pending email verification codes. It returns `{ "email": null, "emailVerified": false }`. A wrong password or rejected request leaves email state and codes unchanged. The current session, credential, username, and foreign accounts remain untouched.

Validation, authorization, dependency, and rate-limit failures use safe responses with `private, no-store`. Metrics contain only fixed outcome labels and duration; email addresses, passwords, account IDs, session IDs, assertions, and hashes are excluded.

The existing service remains the production route owner. Email binding and code delivery, schema ownership, frontend routing, deployment, CI/CD, and production traffic are outside this slice. If this command is activated later, rollback directs only the DELETE path to the existing service; no data migration is required.
