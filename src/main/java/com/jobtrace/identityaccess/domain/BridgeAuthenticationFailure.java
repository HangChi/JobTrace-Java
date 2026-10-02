package com.jobtrace.identityaccess.domain;

/** Safe, bounded diagnostics that never contain identity or token values. */
public enum BridgeAuthenticationFailure {
    ABSENT,
    MALFORMED,
    OVERSIZED,
    UNSUPPORTED_ALGORITHM,
    UNKNOWN_KEY,
    INVALID_SIGNATURE,
    INVALID_CLAIMS,
    REQUEST_MISMATCH,
    REPLAYED,
    REPLAY_STORE_UNAVAILABLE
}
