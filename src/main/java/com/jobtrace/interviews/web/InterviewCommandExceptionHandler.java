package com.jobtrace.interviews.web;

import com.jobtrace.interviews.domain.InterviewWriteException;
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
public class InterviewCommandExceptionHandler {

    @ExceptionHandler(InterviewWriteException.class)
    ResponseEntity<ProblemDetail> writeError(InterviewWriteException exception,
            HttpServletRequest request) {
        HttpStatus status = exception.kind() == InterviewWriteException.Kind.VALIDATION
                ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        String code = exception.kind() == InterviewWriteException.Kind.VALIDATION
                ? "validation" : "conflict";
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        body.setTitle(status.getReasonPhrase());
        body.setType(URI.create("urn:jobtrace:problem:" + code));
        body.setProperty("code", code);
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        body.setProperty("requestId", requestId instanceof String value ? value : "unavailable");
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
