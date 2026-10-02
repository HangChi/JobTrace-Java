package com.jobtrace.applicationdialog.application;

import com.jobtrace.applications.application.GetApplicationDetail;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.interviews.application.ListInterviewsForApplication;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Confirms application ownership before reading any interview summary. */
@Service
public class GetApplicationDialogData {

    private final GetApplicationDetail getApplication;
    private final ListInterviewsForApplication listInterviews;

    public GetApplicationDialogData(
            GetApplicationDetail getApplication,
            ListInterviewsForApplication listInterviews) {
        this.getApplication = getApplication;
        this.listInterviews = listInterviews;
    }

    public ApplicationDialogData execute(String ownerId, UUID applicationId) {
        ApplicationDetail application = getApplication.execute(ownerId, applicationId);
        return new ApplicationDialogData(application,
                listInterviews.execute(ownerId, applicationId));
    }
}
