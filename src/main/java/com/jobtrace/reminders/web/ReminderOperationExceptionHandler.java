package com.jobtrace.reminders.web;

import com.jobtrace.reminders.domain.ReminderOperationException;
import com.jobtrace.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ReminderCommandController.class,
        ReminderPreferencesWriteController.class, ReminderDeliveryController.class})
public class ReminderOperationExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> invalidJson(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return handle(new ReminderOperationException(
                ReminderOperationException.Kind.VALIDATION, "请求内容无效。"), request);
    }

    @ExceptionHandler(ReminderOperationException.class)
    ResponseEntity<ProblemDetail> handle(ReminderOperationException exception,
            HttpServletRequest request) {
        HttpStatus status = switch (exception.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION, EMAIL_UNAVAILABLE -> HttpStatus.BAD_REQUEST;
        };
        String code = switch (exception.kind()) {
            case NOT_FOUND -> "not_found";
            case CONFLICT -> "conflict";
            case VALIDATION -> "validation";
            case EMAIL_UNAVAILABLE -> "email_unavailable";
        };
        var body = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
