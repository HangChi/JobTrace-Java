package com.jobtrace.interviews.domain;

import java.util.regex.Pattern;

/** Matches the legacy public Markdown privacy scrubber for question text. */
public final class PublicInterviewContentSanitizer {

    private static final Pattern PRIVATE_RATING = Pattern.compile(
            "(?:\\r?\\n){1,2}\\*\\*自评分：[^\\r\\n]*\\*\\*");
    private static final Pattern PRIVATE_ACTIONS = Pattern.compile(
            "(?:\\r?\\n){1,2}## 下一步行动(?:\\r?\\n)+.*?(?=(?:\\r?\\n){1,2}##\\s|$)",
            Pattern.DOTALL);
    private static final Pattern EXTRA_NEWLINES = Pattern.compile("\\n{3,}");

    private PublicInterviewContentSanitizer() {}

    public static String sanitize(String value) {
        return EXTRA_NEWLINES.matcher(PRIVATE_ACTIONS.matcher(
                PRIVATE_RATING.matcher(value).replaceAll("")).replaceAll(""))
                .replaceAll("\n\n").trim();
    }
}
