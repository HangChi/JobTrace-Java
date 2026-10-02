package com.jobtrace.interviews.domain;

/** Same external outcome for absent and cross-owner review identifiers. */
public final class InterviewNotFoundException extends RuntimeException {
    public InterviewNotFoundException() {
        super("The interview was not found.");
    }
}
