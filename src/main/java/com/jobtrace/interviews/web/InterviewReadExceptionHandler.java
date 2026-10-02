package com.jobtrace.interviews.web;

import com.jobtrace.interviews.domain.InterviewNotFoundException;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Interview-specific not-found response; global handler owns validation and storage failures. */
@RestControllerAdvice
public class InterviewReadExceptionHandler {

    @ExceptionHandler(InterviewNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The interview was not found.");
        body.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:not_found"));
        body.setProperty("code", "not_found");
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", value instanceof String id ? id : "unavailable");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
