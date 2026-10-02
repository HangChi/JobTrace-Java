package com.jobtrace.interviews.web;

import com.jobtrace.interviews.domain.InterviewListCriteria;
import java.util.List;
import org.springframework.util.MultiValueMap;

/** Preserves repeated legacy query values before strict normalization. */
public final class InterviewListParameters {

    private InterviewListParameters() {}

    public static InterviewListCriteria from(MultiValueMap<String, String> values) {
        return InterviewListCriteria.from(
                values.getFirst("applicationId"), values.getFirst("q"),
                all(values, "status"), all(values, "stage"),
                all(values, "result"), all(values, "publication"),
                values.getFirst("interviewedFrom"), values.getFirst("interviewedTo"),
                values.getFirst("cursor"), values.getFirst("limit"));
    }

    private static List<String> all(MultiValueMap<String, String> values, String key) {
        List<String> found = values.get(key);
        return found == null ? List.of() : found;
    }
}
