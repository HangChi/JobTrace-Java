package com.jobtrace.identityaccess.application;

/** Compatibility boundary for verifying and creating the authoritative password representation. */
public interface PasswordHasher {

    boolean matches(String password, String encodedPassword);

    String hash(String password);
}
