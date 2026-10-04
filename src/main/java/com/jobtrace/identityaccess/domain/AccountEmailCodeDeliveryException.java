package com.jobtrace.identityaccess.domain;

public final class AccountEmailCodeDeliveryException extends RuntimeException {

    private final boolean unavailable;
    private final boolean invalidRecipient;

    public AccountEmailCodeDeliveryException(boolean unavailable, boolean invalidRecipient) {
        super(invalidRecipient ? "邮箱地址不存在或暂时无法接收邮件，请检查后重试。"
                : unavailable ? "邮件服务暂不可用，请稍后再试。"
                : "邮件发送失败，请稍后重试。");
        this.unavailable = unavailable;
        this.invalidRecipient = invalidRecipient;
    }

    public boolean unavailable() {
        return unavailable;
    }

    public boolean invalidRecipient() {
        return invalidRecipient;
    }
}
