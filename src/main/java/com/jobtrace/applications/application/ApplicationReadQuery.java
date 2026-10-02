package com.jobtrace.applications.application;

import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationListCriteria;
import com.jobtrace.applications.domain.ApplicationPage;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationReadQuery {

    ApplicationPage list(String ownerId, ApplicationListCriteria criteria, LocalDate today);

    Optional<ApplicationDetail> findDetail(String ownerId, UUID id, LocalDate today);
}
