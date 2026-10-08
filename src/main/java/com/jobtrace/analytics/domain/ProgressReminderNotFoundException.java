package com.jobtrace.analytics.domain;

public class ProgressReminderNotFoundException extends RuntimeException {
    public ProgressReminderNotFoundException() {
        super("没有找到这条待处理进展。");
    }
}
