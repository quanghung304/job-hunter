package io.github.quanghung304.jobhunter.api;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import io.github.quanghung304.jobhunter.llm.LlmAlertService;
import io.github.quanghung304.jobhunter.llm.LlmPausedException;
import io.github.quanghung304.jobhunter.llm.PaidConfirmationRequiredException;
import io.github.quanghung304.jobhunter.profile.ProfileValidationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    /** Extra property of an invalid profile: {@code [{path, message}]} (api-spec "Profile"). */
    static final String ERRORS = "errors";

    /** Extra property of {@code LLM_PAUSED}: the {@code LlmAlert} of the paused purpose (T-022). */
    static final String ALERT = "alert";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectProvider<LlmAlertService> alerts;

    /** Without alerts (standalone controller tests). */
    public GlobalExceptionHandler() {
        this.alerts = null;
    }

    @Autowired
    public GlobalExceptionHandler(ObjectProvider<LlmAlertService> alerts) {
        this.alerts = alerts;
    }

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
            LlmAlertService service = alerts == null ? null : alerts.getIfAvailable();
            if (service != null) {
                service.alert(paused.purpose()).map(LlmAlertResponse::of)
                        .ifPresent(alert -> problem.setProperty(ALERT, alert));
            }
        }
        if (ex instanceof PaidConfirmationRequiredException confirm) {
            problem.setProperty("modelId", confirm.model().modelId());
            problem.setProperty("modelLabel", confirm.model().label());
            problem.setProperty("tier", confirm.model().tier().name());
            problem.setProperty("estimatedCostPerCallUsd", confirm.estimatedCostPerCallUsd());
        }
        if (ex instanceof ProfileValidationException invalid) {
            problem.setProperty(ERRORS, invalid.problems().stream()
                    .map(p -> Map.of("path", p.path(), "message", p.message()))
                    .toList());
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
