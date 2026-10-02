package com.jobtrace.applications.domain;

public final class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException() {
        super("The application was not found.");
    }
}
