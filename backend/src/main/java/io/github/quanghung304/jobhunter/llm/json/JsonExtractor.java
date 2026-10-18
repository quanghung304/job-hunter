package io.github.quanghung304.jobhunter.llm.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Turns a model answer into a validated object: takes the first top-level JSON object in the
 * text (models sometimes add prose or code fences), maps it with Jackson (unknown fields
 * ignored) and runs Bean Validation.
 */
@Component
public class JsonExtractor {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Validator validator;

    public JsonExtractor(Validator validator) {
        this.validator = validator;
    }

    /** @throws InvalidLlmOutputException with a message suitable for the corrective retry */
    public <T> T extract(String text, Class<T> type) {
        String json = firstObject(text)
                .orElseThrow(() -> new InvalidLlmOutputException("The answer contains no JSON object."));
        T value;
        try {
            value = JSON.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new InvalidLlmOutputException("The JSON object does not match the expected shape: "
                    + e.getOriginalMessage());
        }
        if (value == null) {
            throw new InvalidLlmOutputException("The answer is JSON null.");
        }
        var violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new InvalidLlmOutputException("The JSON object is invalid: " + violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(JsonExtractor::describe)
                    .collect(Collectors.joining("; ")));
        }
        return value;
    }

    /** The first balanced {@code {...}} in the text, respecting JSON strings and escapes. */
    static Optional<String> firstObject(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = matchingBrace(text, start);
            if (end < 0) {
                return Optional.empty();
            }
            String candidate = text.substring(start, end + 1);
            if (isJson(candidate)) {
                return Optional.of(candidate);
            }
            start = text.indexOf('{', start + 1);
        }
        return Optional.empty();
    }

    private static int matchingBrace(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isJson(String candidate) {
        try {
            JSON.readTree(candidate);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    private static String describe(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }
}
