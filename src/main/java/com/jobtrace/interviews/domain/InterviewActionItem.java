package com.jobtrace.interviews.domain;

import java.util.Objects;

public record InterviewActionItem(String id, String content, boolean completed) {
    public InterviewActionItem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(content);
    }
}
