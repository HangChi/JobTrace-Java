package com.jobtrace.applications.application;

import com.jobtrace.applications.domain.ApplicationMutation.Stage;
import java.time.LocalDate;
import java.util.UUID;

public interface ApplicationStageStore {
    void add(String ownerId, UUID applicationId, Stage stage);
    void update(String ownerId, UUID applicationId, UUID occurrenceId,
            int expectedVersion, Stage stage, LocalDate changeDate);
    void remove(String ownerId, UUID applicationId, UUID occurrenceId, LocalDate changeDate);
}
