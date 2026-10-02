package com.jobtrace.applications.web;

import com.jobtrace.applications.domain.ApplicationListCriteria;
import java.util.List;
import org.springframework.util.MultiValueMap;

/** Preserves repeated legacy query parameters and delegates normalization to the domain. */
public final class ApplicationListParameters {

    private ApplicationListParameters() {}

    public static ApplicationListCriteria from(MultiValueMap<String, String> parameters) {
        return ApplicationListCriteria.from(
                first(parameters, "q"),
                all(parameters, "status"),
                all(parameters, "type"),
                all(parameters, "stage"),
                all(parameters, "city"),
                first(parameters, "appliedFrom"),
                first(parameters, "appliedTo"),
                first(parameters, "sort"),
                first(parameters, "direction"),
                first(parameters, "cursor"),
                first(parameters, "page"),
                first(parameters, "limit"));
    }

    private static String first(MultiValueMap<String, String> values, String key) {
        return values.getFirst(key);
    }

    private static List<String> all(MultiValueMap<String, String> values, String key) {
        List<String> found = values.get(key);
        return found == null ? List.of() : found;
    }
}
