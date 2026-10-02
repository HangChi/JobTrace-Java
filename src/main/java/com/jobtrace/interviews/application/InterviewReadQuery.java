package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.InterviewDetail;
import com.jobtrace.interviews.domain.InterviewListCriteria;
import com.jobtrace.interviews.domain.InterviewPage;
import com.jobtrace.interviews.domain.StageInterviewSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Every operation is explicitly scoped by a trusted owner. */
public interface InterviewReadQuery {
    InterviewPage list(String ownerId, InterviewListCriteria criteria);
    Optional<InterviewDetail> findDetail(String ownerId, UUID id);
    List<StageInterviewSummary> listForApplication(String ownerId, UUID applicationId);
}
