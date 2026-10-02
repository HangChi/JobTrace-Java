package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.StageInterviewSummary;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ListInterviewsForApplication {

    private final InterviewReadQuery query;

    public ListInterviewsForApplication(InterviewReadQuery query) {
        this.query = query;
    }

    public List<StageInterviewSummary> execute(String ownerId, UUID applicationId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.listForApplication(ownerId, Objects.requireNonNull(applicationId));
    }
}
