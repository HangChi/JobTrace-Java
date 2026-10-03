package com.jobtrace.identityaccess.domain;

/** The password-bearing credential selected only after owner/session validation. */
public record AccountPasswordCredential(String accountId, String passwordHash) {

    public AccountPasswordCredential {
        if (accountId == null || accountId.isBlank() || accountId.length() > 255) {
            throw new IllegalArgumentException("accountId must contain 1 through 255 characters");
        }
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 512) {
            throw new IllegalArgumentException("passwordHash must contain 1 through 512 characters");
        }
    }
}
