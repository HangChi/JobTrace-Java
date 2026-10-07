package com.jobtrace.interviews.domain;

public final class PublicInterviewNotFoundException extends RuntimeException {
    public PublicInterviewNotFoundException() {
        super("没有找到这篇公开面经。");
    }
}
