package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.PublicInterviewCriteria;
import com.jobtrace.interviews.domain.PublicInterviewPage;
import com.jobtrace.interviews.domain.PublicInterviewPage.Item;
import java.util.Optional;
import java.util.UUID;

public interface PublicInterviewQuery {
    PublicInterviewPage list(String viewerId, PublicInterviewCriteria criteria);
    Optional<Item> findDetail(String viewerId, UUID id);
}
