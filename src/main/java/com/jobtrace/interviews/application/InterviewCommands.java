package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.InterviewDetail;
import com.jobtrace.interviews.domain.InterviewNotFoundException;
import com.jobtrace.interviews.domain.InterviewMutation.Create;
import com.jobtrace.interviews.domain.InterviewMutation.Update;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InterviewCommands {

    private final InterviewCommandStore store;
    private final GetPrivateInterview getDetail;

    public InterviewCommands(InterviewCommandStore store, GetPrivateInterview getDetail) {
        this.store = store;
        this.getDetail = getDetail;
    }

    public InterviewDetail create(String ownerId, Create command) {
        return getDetail.execute(ownerId, store.create(ownerId, command));
    }

    public InterviewDetail update(String ownerId, UUID id, Update command) {
        store.update(ownerId, id, command);
        return getDetail.execute(ownerId, id);
    }

    public void delete(String ownerId, UUID id) {
        if (!store.delete(ownerId, id)) {
            throw new InterviewNotFoundException();
        }
    }
}
