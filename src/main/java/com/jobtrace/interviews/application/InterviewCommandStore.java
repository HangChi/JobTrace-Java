package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.InterviewMutation.Create;
import com.jobtrace.interviews.domain.InterviewMutation.Update;
import java.util.UUID;

public interface InterviewCommandStore {
    UUID create(String ownerId, Create command);
    void update(String ownerId, UUID id, Update command);
    boolean delete(String ownerId, UUID id);
}
