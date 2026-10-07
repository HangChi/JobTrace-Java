package com.jobtrace.interviews.domain;

public record PublicInterviewCriteria(String query, String city, String position,
        Sort sort, String cursor, int limit) {

    public enum Sort { LATEST, HOT }

    public PublicInterviewCriteria {
        if (query == null || query.length() > 200 || city == null || city.length() > 100
                || position == null || position.length() > 200 || sort == null
                || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Invalid public interview filters");
        }
    }
}
