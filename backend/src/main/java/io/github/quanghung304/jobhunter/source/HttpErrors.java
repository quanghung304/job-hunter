package io.github.quanghung304.jobhunter.source;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Short, log-safe descriptions of HTTP failures (no response bodies, e.g. Cloudflare pages). */
public final class HttpErrors {

    static final int MAX_LENGTH = 300;

    private HttpErrors() {
    }

    /** "HTTP 429 Too Many Requests" for error responses, otherwise the (shortened) message. */
    public static String describe(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            String reason = response.getStatusText() == null ? "" : response.getStatusText().strip();
            return ("HTTP " + response.getStatusCode().value() + " " + reason).strip();
        }
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() <= MAX_LENGTH ? message : message.substring(0, MAX_LENGTH) + "...";
    }
}
