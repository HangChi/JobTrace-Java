package com.jobtrace.applicationdialog.application;

import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.interviews.domain.StageInterviewSummary;
import java.util.List;
import java.util.Objects;

public record ApplicationDialogData(
        ApplicationDetail application, List<StageInterviewSummary> interviews) {
    public ApplicationDialogData {
        Objects.requireNonNull(application);
        interviews = List.copyOf(Objects.requireNonNull(interviews));
    }
}
