package com.jobtrace.applications.application;

import com.jobtrace.applications.domain.ApplicationCatalog.ApplicationStatus;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.applications.domain.ApplicationMutation;
import com.jobtrace.applications.domain.ApplicationNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import static com.jobtrace.shared.config.TimeConfiguration.BUSINESS_ZONE;

@Service
public class ApplicationCommands {

    private final ApplicationCommandStore store;
    private final GetApplicationDetail getDetail;
    private final Clock clock;

    public ApplicationCommands(ApplicationCommandStore store, GetApplicationDetail getDetail, Clock clock) {
        this.store = store;
        this.getDetail = getDetail;
        this.clock = clock;
    }

    public ApplicationDetail create(String ownerId, ApplicationMutation command) {
        return getDetail.execute(ownerId, store.create(ownerId, command));
    }

    public ApplicationDetail update(String ownerId, UUID id, ApplicationMutation command) {
        store.update(ownerId, id, command);
        return getDetail.execute(ownerId, id);
    }

    public ApplicationCommandStore.StatusResult updateStatus(
            String ownerId, UUID id, int version, ApplicationStatus status) {
        return store.updateStatus(ownerId, id, version, status,
                LocalDate.now(clock.withZone(BUSINESS_ZONE)));
    }

    public void delete(String ownerId, UUID id) {
        if (!store.delete(ownerId, id)) {
            throw new ApplicationNotFoundException();
        }
    }

    public int deleteMany(String ownerId, List<UUID> ids) {
        return store.deleteMany(ownerId, ids);
    }
}
