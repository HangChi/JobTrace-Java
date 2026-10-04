# Research: Account Password Change

## Established transport behavior

**Decision**: Preserve `POST /api/profile/password`, the `currentPassword` and `newPassword` object fields, the 8-to-16 replacement length, `invalid_password` field error semantics, and the success message `密码已更新，其他设备已退出登录。` Unknown object properties remain ignored.

**Rationale**: These values are consumed by the current profile credential form and asserted by the legacy contract suite.

**Alternatives considered**: A new endpoint or stricter unknown-field rejection would require an unnecessary client migration.

## Password representation compatibility

**Decision**: Reproduce Better Auth 1.6.27's default representation exactly: NFKC-normalized UTF-8 password input, a random 16-byte salt encoded as 32 lowercase hexadecimal characters, scrypt with `N=16384`, `r=16`, `p=1`, and a 64-byte derived key, serialized as `saltHex:keyHex`. Use Bouncy Castle 1.86's maintained scrypt implementation and constant-time byte comparison.

**Rationale**: The same stored value must remain verifiable by both Java and the existing Better Auth authority during incremental migration. Java SE does not provide scrypt, while a handwritten cryptographic implementation would add avoidable security and maintenance risk. Bouncy Castle 1.86 is the current stable Java provider release selected for the Java 8+ artifact.

**Alternatives considered**: Spring Security's encoded scrypt envelope is not storage-compatible; BCrypt or PBKDF2 would break the legacy verifier; delegating to the legacy network runtime would add availability and deployment coupling.

## Atomic credential and session change

**Decision**: Read only the owner's credential hash after proving that the supplied current session is active and owner-scoped. After verification and hashing, run one transaction that locks the current session, conditionally replaces the same credential hash, and deletes only other sessions for the owner.

**Rationale**: Conditional replacement detects credential races. Locking and revalidating the current session prevents a caller-supplied foreign or missing session from authorizing mutation. A transaction guarantees that credential and session changes either both commit or both roll back.

**Alternatives considered**: Independent update/delete statements can partially succeed; preserving an unchecked session identifier can retain the wrong session; deleting all sessions would require Java to issue a replacement session, which is outside this slice.

## Security and diagnostics

**Decision**: Protect only exact POST on the password path, require the signed owner principal plus bounded current-session metadata, apply `private, no-store`, and emit only allow-listed outcomes and latency measurements.

**Rationale**: This keeps authority as narrow as the migrated operation and prevents secrets or identity data from reaching caches, metrics, and logs.

**Alternatives considered**: Protecting all profile methods widens bridge authority; logging password failures with values or identifiers is unsafe.
