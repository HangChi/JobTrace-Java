package com.jobtrace.identityaccess.domain;

/** Stable public outcome for configured human-verification failures. */
public final class RegistrationChallengeException extends RuntimeException {

    public enum Reason { REQUIRED, FAILED, UNAVAILABLE }

    private final Reason reason;

    public RegistrationChallengeException(Reason reason) {
        super(switch (reason) {
            case REQUIRED -> "请完成人机验证。";
            case FAILED -> "人机验证失败。";
            case UNAVAILABLE -> "暂时无法完成人机验证，请稍后重试。";
        });
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
