package com.jobtrace.applications.application;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationMutation;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ApplicationCommandStore {
    UUID create(String ownerId, ApplicationMutation command);
    void update(String ownerId, UUID id, ApplicationMutation command);
    StatusResult updateStatus(String ownerId, UUID id, int version,
            ApplicationStatus status, LocalDate changeDate);
    boolean delete(String ownerId, UUID id);
    int deleteMany(String ownerId, List<UUID> ids);

    record StatusResult(UUID id, ApplicationStatus status, LocalDate latestDate, int version) {}
}
