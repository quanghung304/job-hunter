package io.github.quanghung304.jobhunter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/** Reads captured source responses from {@code src/test/resources/fixtures/<code>/}. */
public final class Fixtures {

    public static final ObjectMapper JSON = new ObjectMapper();

    private Fixtures() {
    }

    public static String text(String path) {
        try {
            return new ClassPathResource("fixtures/" + path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("fixture " + path, e);
        }
    }

    public static JsonNode json(String path) {
        try {
            return JSON.readTree(text(path));
        } catch (IOException e) {
            throw new UncheckedIOException("fixture " + path, e);
        }
    }
}
