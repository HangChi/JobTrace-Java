package com.jobtrace.identityaccess.application;

/** Legacy-compatible HMAC boundary for owner-bound email verification codes. */
public interface EmailVerificationCodeHasher {

    String hash(String ownerId, String email, String code);
}
