package com.jobtrace.interviews.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtrace.interviews.domain.InterviewWriteException;
import com.jobtrace.interviews.domain.PublicInterviewNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class PublicInterviewExceptionHandlerTest {

    @Test
    void notFoundOmitsUnavailableRequestIdAndContent() {
        var response = new PublicInterviewExceptionHandler().notFound(
                new PublicInterviewNotFoundException(), new MockHttpServletRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getProperties()).containsEntry("requestId", "unavailable");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void writeValidationMapsToBadRequestWithoutRequestId() {
        var response = new InterviewCommandExceptionHandler().writeError(
                new InterviewWriteException(InterviewWriteException.Kind.VALIDATION,
                        "Invalid review"), new MockHttpServletRequest());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getProperties()).containsEntry("code", "validation")
                .containsEntry("requestId", "unavailable");
    }
}
