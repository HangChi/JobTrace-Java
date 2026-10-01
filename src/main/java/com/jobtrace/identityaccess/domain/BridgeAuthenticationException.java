package com.jobtrace.identityaccess.domain;

/** Internal authentication failure with a safe, bounded classification. */
public final class BridgeAuthenticationException extends RuntimeException {

    private final BridgeAuthenticationFailure failure;

    public BridgeAuthenticationException(BridgeAuthenticationFailure failure) {
        super("Signed identity assertion rejected");
        this.failure = failure;
    }

    public BridgeAuthenticationException(
            BridgeAuthenticationFailure failure,
            Throwable cause) {
        super("Signed identity assertion rejected", cause);
        this.failure = failure;
    }

    public BridgeAuthenticationFailure failure() {
        return failure;
    }
}
