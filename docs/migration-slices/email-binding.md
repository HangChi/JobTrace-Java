# Account Email Binding (Feature 016)

This slice adds the authenticated `PATCH /api/profile/email` command for binding an email after current-password and existing verification-code checks. A signed bridge assertion must match the exact method, path, and request ID. Trusted current-session metadata must identify an active session owned by the verified subject.

The command shares the existing `email-change` limit of five attempts per fifteen minutes with unbinding. The six-digit code is normalized and HMAC-SHA256 hashed using the same purpose, owner, email, and secret format as the existing service. PostgreSQL's existing `verify_email_code_attempt` function selects and locks the newest eligible code, increments its attempt count on a wrong code, and consumes it on success. The email update and successful code consumption commit atomically. A conflicting email rolls both back, so the code remains usable. The owner's recovery email and verification state are updated; credentials, sessions, username, and foreign accounts are not.

Validation, authorization, dependency, conflict, and rate-limit failures use safe responses with `private, no-store`. Metrics use only fixed outcome labels and duration, with no email address, password, code, owner ID, session ID, assertion, or hash.

The existing service remains the production route owner and continues to issue and deliver verification codes. Java requires `JOBTRACE_EMAIL_BINDING_VERIFICATION_SECRET` to contain the same secret as the existing service before this command can be activated. Code issuance, mail delivery, schema ownership, frontend routing, deployment, CI/CD, and production traffic are outside this slice. Rollback directs only the PATCH path to the existing service; no data migration is required.
