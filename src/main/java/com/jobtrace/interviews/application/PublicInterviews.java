package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.PublicInterviewCriteria;
import com.jobtrace.interviews.domain.PublicInterviewNotFoundException;
import com.jobtrace.interviews.domain.PublicInterviewPage;
import com.jobtrace.interviews.domain.PublicInterviewPage.Item;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PublicInterviews {

    private final PublicInterviewQuery query;

    public PublicInterviews(PublicInterviewQuery query) {
        this.query = query;
    }

    public PublicInterviewPage list(String viewerId, PublicInterviewCriteria criteria) {
        return query.list(viewerId, criteria);
    }

    public Item detail(String viewerId, UUID id) {
        return query.findDetail(viewerId, id)
                .orElseThrow(PublicInterviewNotFoundException::new);
    }
}
