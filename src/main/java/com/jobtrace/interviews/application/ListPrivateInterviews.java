package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.InterviewListCriteria;
import com.jobtrace.interviews.domain.InterviewPage;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListPrivateInterviews {

    private final InterviewReadQuery query;

    public ListPrivateInterviews(InterviewReadQuery query) {
        this.query = query;
    }

    public InterviewPage execute(String ownerId, InterviewListCriteria criteria) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.list(ownerId, Objects.requireNonNull(criteria));
    }
}
