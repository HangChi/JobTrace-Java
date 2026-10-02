package com.jobtrace.applications.application;

import static com.jobtrace.shared.config.TimeConfiguration.BUSINESS_ZONE;

import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetApplicationDetail {

    private final ApplicationReadQuery query;
    private final Clock clock;

    public GetApplicationDetail(ApplicationReadQuery query, Clock clock) {
        this.query = query;
        this.clock = clock;
    }

    public ApplicationDetail execute(String ownerId, UUID id) {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        return query.findDetail(
                        ownerId,
                        Objects.requireNonNull(id),
                        LocalDate.now(clock.withZone(BUSINESS_ZONE)))
                .orElseThrow(ApplicationNotFoundException::new);
    }
}
