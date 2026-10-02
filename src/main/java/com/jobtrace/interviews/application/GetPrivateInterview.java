package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.InterviewDetail;
import com.jobtrace.interviews.domain.InterviewNotFoundException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetPrivateInterview {

    private final InterviewReadQuery query;

    public GetPrivateInterview(InterviewReadQuery query) {
        this.query = query;
    }

    public InterviewDetail execute(String ownerId, UUID id) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.findDetail(ownerId, Objects.requireNonNull(id))
                .orElseThrow(InterviewNotFoundException::new);
    }
}
