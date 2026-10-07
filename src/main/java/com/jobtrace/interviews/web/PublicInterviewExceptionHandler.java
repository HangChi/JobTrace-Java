package com.jobtrace.interviews.web;

import com.jobtrace.interviews.domain.PublicInterviewNotFoundException;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PublicInterviewExceptionHandler {

    @ExceptionHandler(PublicInterviewNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(PublicInterviewNotFoundException exception,
            HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
        body.setTitle(HttpStatus.NOT_FOUND.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:not_found"));
        body.setProperty("code", "not_found");
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .cacheControl(CacheControl.noStore()).body(body);
    }
}
