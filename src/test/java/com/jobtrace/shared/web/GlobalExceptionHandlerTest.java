package com.jobtrace.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataAccessResourceFailureException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsKnownProblemsWithoutLeakingAdditionalData() {
        MockHttpServletRequest request = requestWithId("request-1");

        var response = handler.handleProblem(
                new Problem("application_not_found", "Application not found.", HttpStatus.NOT_FOUND),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getDetail()).isEqualTo("Application not found.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "application_not_found")
                .containsEntry("requestId", "request-1");
    }

    @Test
    void mapsUnexpectedFailuresToAGenericResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        var response = handler.handleUnexpected(new IllegalStateException("sensitive detail"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("The request could not be completed.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "internal_error")
                .containsEntry("requestId", "unavailable");
    }

    @Test
    void mapsStorageFailureToSafeUnavailableProblem() {
        var response = handler.handleStorageUnavailable(
                new DataAccessResourceFailureException("sensitive database address"),
                requestWithId("request-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getDetail()).isEqualTo("The data store is unavailable.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "storage_unavailable")
                .containsEntry("requestId", "request-1");
    }

    private MockHttpServletRequest requestWithId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
