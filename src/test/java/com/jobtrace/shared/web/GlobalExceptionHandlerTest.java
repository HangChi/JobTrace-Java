package com.jobtrace.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

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

    @Test
    void mapsMultipartLimitAndMediaTypeToSafeClientErrors() {
        var request = requestWithId("request-2");
        var oversized = handler.handleOversizedUpload(
                new MaxUploadSizeExceededException(5 * 1024 * 1024), request);
        assertThat(oversized.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(oversized.getBody().getProperties())
                .containsEntry("code", "payload_too_large");
        var unsupported = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException("application/octet-stream"), request);
        assertThat(unsupported.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(unsupported.getBody().getProperties())
                .containsEntry("code", "unsupported_format");
    }

    private MockHttpServletRequest requestWithId(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);
        return request;
    }
}
