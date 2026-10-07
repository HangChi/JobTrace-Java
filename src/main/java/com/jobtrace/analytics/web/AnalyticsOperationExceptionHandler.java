package com.jobtrace.analytics.web;

import com.jobtrace.analytics.domain.InvalidAnalyticsRangeException;
import com.jobtrace.analytics.domain.ProgressReminderNotFoundException;
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
public class AnalyticsOperationExceptionHandler {

    @ExceptionHandler(ProgressReminderNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(
            ProgressReminderNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not_found", exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidAnalyticsRangeException.class)
    ResponseEntity<ProblemDetail> invalidRange(
            InvalidAnalyticsRangeException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "validation", exception.getMessage(), request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message);
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
