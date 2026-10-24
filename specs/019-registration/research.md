# Research: Verified Account Registration

## Legacy contract

**Decision**: Mirror `POST /api/auth/register` and its `202` `{ "message": "账号已创建，请登录。" }` response.

**Rationale**: The old route checks same Origin, limits source to five per minute, optionally verifies an `x-auth-challenge`, validates input, verifies a registration-purpose code, then creates a Better Auth credential without auto-sign-in.

**Alternatives considered**: Migrating login and session issuance with registration; rejected as a broader security boundary.

## Code verification

**Decision**: Call the existing `verify_email_code_attempt(email,'registration',null,hash)` function outside the creation transaction.

**Rationale**: Wrong attempts persist and matching codes are consumed even when later uniqueness checks fail, preserving old behavior. The function serializes concurrent attempts.

**Alternatives considered**: Selecting the hash directly or enclosing verification in the creation transaction; the former breaks attempt semantics, while the latter rolls back wrong-attempt counts on failure.

## Account persistence

**Decision**: Insert the user and credential in one transaction with the old internal email and username conventions; use the existing Better Auth scrypt encoder.

**Rationale**: The old implementation creates a user, binds recovery email, and compensates if binding fails. One Java transaction eliminates orphan accounts while preserving the final record shape.

**Alternatives considered**: Calling Better Auth from Java or compensating after two writes; both increase failure modes without adding compatibility.

## Challenge and source identity

**Decision**: Preserve the optional configured challenge endpoint and a fail-closed five-second call. Default source key remains `local-register` unless trusted-proxy parsing is separately reviewed.

**Rationale**: The old service ignores untrusted `X-Forwarded-For` by default; challenge verification precedes account creation.

**Alternatives considered**: Trusting arbitrary forwarded IPs; rejected because clients could evade limits.
