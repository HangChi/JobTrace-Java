package com.jobtrace.datatransfer.infrastructure;

import com.jobtrace.datatransfer.domain.InterviewExportDocument;
import java.util.ArrayList;
import java.util.List;

/** Current-service Markdown and safe filename representation. */
public class InterviewMarkdownWriter {

    public String filename(InterviewExportDocument document) {
        String stage = switch (document.stage()) {
            case "assessment" -> "测评";
            case "interview_1" -> "一面";
            case "interview_2" -> "二面";
            case "interview_3" -> "三面";
            case "hr_interview" -> "HR面";
            case "final_interview" -> "终面";
            default -> "面试";
        };
        String duration = document.durationMinutes() == null || document.durationMinutes() == 0
                ? "时长未记录" : document.durationMinutes() + "分钟";
        return safeFilenamePart(document.companyName()) + "-"
                + safeFilenamePart(document.positionName()) + "-" + stage + "面经-" + duration + ".md";
    }

    public static String safeFilenamePart(String value) {
        String trimmed = value.trim().replaceAll("[<>:\"/\\\\|?*\\x00-\\x1f]", "-")
                .replaceAll("\\s+", " ").replaceAll("[-. ]+$", "");
        String safe = trimmed.substring(0, Math.min(60, trimmed.length()));
        return safe.isEmpty() ? "未命名" : safe;
    }

    public String content(InterviewExportDocument document) {
        var questions = document.questions();
        var first = questions.isEmpty() ? null : questions.getFirst();
        if (questions.size() == 1 && empty(first.originalAnswer())
                && empty(first.followUpNotes()) && empty(first.improvedAnswer())
                && first.selfRating() == null && empty(document.highlights())
                && empty(document.gaps()) && document.actionCount() == 0) {
            return first.text();
        }
        var sections = new ArrayList<String>();
        for (int index = 0; index < questions.size(); index++) {
            var question = questions.get(index);
            sections.add(questions.size() > 1 ? "## 问题 " + (index + 1) : "## 面试问题");
            sections.add(question.text());
            add(sections, "### 当时的回答", question.originalAnswer());
            add(sections, "### 追问或反馈", question.followUpNotes());
            add(sections, "### 复盘后的回答", question.improvedAnswer());
        }
        add(sections, "## 做得好的地方", document.highlights());
        add(sections, "## 可以改进的地方", document.gaps());
        return String.join("\n\n", sections);
    }

    private static void add(List<String> sections, String heading, String value) {
        if (value != null && !value.isEmpty()) {
            sections.add(heading);
            sections.add(value);
        }
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
