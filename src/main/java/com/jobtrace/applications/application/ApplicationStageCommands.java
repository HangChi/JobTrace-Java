package com.jobtrace.applications.application;

import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationMutation.Stage;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationStageCommands {

    private final ApplicationStageStore store;
    private final GetApplicationDetail getDetail;

    public ApplicationStageCommands(ApplicationStageStore store, GetApplicationDetail getDetail) {
        this.store = store;
        this.getDetail = getDetail;
    }

    public ApplicationDetail add(String ownerId, UUID applicationId, Stage stage) {
        store.add(ownerId, applicationId, stage);
        return getDetail.execute(ownerId, applicationId);
    }

    public ApplicationDetail update(String ownerId, UUID applicationId, UUID occurrenceId,
            int version, Stage stage, LocalDate changeDate) {
        store.update(ownerId, applicationId, occurrenceId, version, stage, changeDate);
        return getDetail.execute(ownerId, applicationId);
    }

    public ApplicationDetail remove(String ownerId, UUID applicationId, UUID occurrenceId,
            LocalDate changeDate) {
        store.remove(ownerId, applicationId, occurrenceId, changeDate);
        return getDetail.execute(ownerId, applicationId);
    }
}
