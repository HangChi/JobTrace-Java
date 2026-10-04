# Data Model: Account Password Change

## Password Change Command

| Field | Rule |
|---|---|
| ownerId | Verified bridge subject, 1 through 128 characters |
| currentSessionId | Opaque identifier, 1 through 255 characters, active and owned by `ownerId` |
| currentPassword | String, 1 through 128 characters |
| newPassword | String, 8 through 16 characters |

## Credential Snapshot

| Field | Rule |
|---|---|
| accountId | Opaque credential-account identifier |
| passwordHash | Exactly 32 lowercase hex salt characters, colon, and 128 lowercase hex derived-key characters |

Only an `accounts` row with `provider_id = 'credential'`, non-null password, and matching owner may be selected.

## Password Change Result

| Field | Rule |
|---|---|
| message | Stable value `密码已更新，其他设备已退出登录。` |

## State transition

Before: the verified owner has one compatible credential hash and an active current session, optionally plus other sessions.

Success: the credential row contains a fresh compatible hash, the current session remains, and every other session for the owner is deleted in the same transaction. User data and foreign credentials/sessions are unchanged.

Failure: wrong password, malformed hash, missing credential, missing/foreign/expired current session, a credential race, or a storage failure leaves the credential and every session unchanged.
