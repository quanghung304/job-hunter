package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import io.github.quanghung304.jobhunter.llm.LlmPausedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to RFC 9457 Problem Details with a stable {@code code} property
 * (api-spec.md, "Conventions").
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String CODE = "code";
    static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    /** Extra property of {@code LLM_PAUSED}: the paused purpose (EXTRACT or SCORE). */
    static final String PURPOSE = "purpose";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JobHunterException.class)
    ResponseEntity<ProblemDetail> handleJobHunter(JobHunterException ex) {
        var status = HttpStatus.valueOf(ex.httpStatus());
        if (status.is5xxServerError() && status != HttpStatus.NOT_IMPLEMENTED) {
            log.error("Request failed: code={}", ex.code(), ex);
        }
        var problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty(CODE, ex.code());
        if (ex instanceof LlmPausedException paused) {
            problem.setProperty(PURPOSE, paused.purpose().name());
        }
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        problem.setProperty(CODE, INTERNAL_ERROR);
        return ResponseEntity.internalServerError().body(problem);
    }

    /** Adds a {@code code} to the Problem Details Spring MVC builds for its own exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode,
            WebRequest request) {
        var response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem && !hasCode(problem)) {
            problem.setProperty(CODE, codeFor(ex, statusCode));
        }
        return response;
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey(CODE);
    }

    private static String codeFor(Exception ex, HttpStatusCode statusCode) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return VALIDATION_FAILED;
        }
        var status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }
}
