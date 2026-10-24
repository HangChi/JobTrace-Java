package com.jobtrace.identityaccess.application;

public interface RegistrationEmailCodeHasher {

    String hash(String email, String code);
}
