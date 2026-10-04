# Data Model: Complete Account Authentication

## Browser session

- Existing `sessions`: opaque ID, token, owner ID, expiry, creation/update times and user agent.
- Valid only while expiry is in the future and the linked user exists and is enabled.
- The token is never returned in profile/session responses or logs.

## Account

- Existing `users`: ID, display name, role, disabled state, recovery email and verification time, image URL.
- Authoritative role and enabled state are read afresh for browser authorization.

## Password reset grant

- Existing `verification_tokens`: ID, `reset-password:<token>` identifier, owner ID value, expiry.
- State transitions: issued -> atomically consumed; issued -> expired. Neither consumed nor expired grants can change a credential.
- Successful consumption updates the credential hash and revokes all owner sessions in the same transaction.

## Avatar object

- COS key `avatars/<owner-id>/<random-id>.<extension>`; validated bytes, MIME type and resulting public URL.
- Upload does not itself change `users.image`; the owner may apply that URL through existing profile update.
