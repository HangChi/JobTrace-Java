package com.jobtrace.applications.application;

import static com.jobtrace.shared.config.TimeConfiguration.BUSINESS_ZONE;

import com.jobtrace.applications.domain.ApplicationListCriteria;
import com.jobtrace.applications.domain.ApplicationPage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ListApplications {

    private final ApplicationReadQuery query;
    private final Clock clock;

    public ListApplications(ApplicationReadQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    public ApplicationPage execute(String ownerId, ApplicationListCriteria criteria) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.list(
                ownerId,
                Objects.requireNonNull(criteria),
                LocalDate.now(clock.withZone(BUSINESS_ZONE)));
    }
}
