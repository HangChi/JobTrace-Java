package com.jobtrace.applications.web;

import com.jobtrace.applications.domain.ApplicationNotFoundException;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApplicationReadExceptionHandler {

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(
            ApplicationNotFoundException exception,
            HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "The application was not found.");
        body.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:not_found"));
        body.setProperty("code", "not_found");
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
