package com.jobtrace.admin.domain;

public class InvalidAdminAccessCommandException extends RuntimeException {

    public InvalidAdminAccessCommandException(String message) {
        super(message);
    }
}
