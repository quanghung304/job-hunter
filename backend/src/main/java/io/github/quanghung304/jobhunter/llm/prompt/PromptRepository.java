package io.github.quanghung304.jobhunter.llm.prompt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Loads prompt files from {@code classpath:prompts/<id>.md} (packaged from docs/ai/prompts)
 * and caches them. Format: docs/ai/prompts/README.md.
 */
@Component
public class PromptRepository {

    private static final Pattern VALID_ID = Pattern.compile("[a-z]+-v\\d+");
    private static final String SYSTEM_HEADING = "## system";
    private static final String USER_HEADING = "## user";
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final ClassLoader classLoader;
    private final Map<String, PromptTemplate> cache = new ConcurrentHashMap<>();

    public PromptRepository() {
        this(PromptRepository.class.getClassLoader());
    }

    PromptRepository(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public PromptTemplate get(String promptId) {
        return cache.computeIfAbsent(promptId, this::load);
    }

    private PromptTemplate load(String promptId) {
        if (!VALID_ID.matcher(promptId).matches()) {
            throw new InvalidPromptException(promptId, "id must look like <purpose>-v<n>");
        }
        try (InputStream in = classLoader.getResourceAsStream("prompts/" + promptId + ".md")) {
            if (in == null) {
                throw new InvalidPromptException(promptId, "file prompts/" + promptId + ".md not found on the classpath");
            }
            return parse(promptId, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new InvalidPromptException(promptId, "cannot read file: " + e.getMessage(), e);
        }
    }

    static PromptTemplate parse(String promptId, String content) {
        String text = content.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new InvalidPromptException(promptId, "must start with a '---' front matter line");
        }
        int end = text.indexOf("\n---\n", 3);
        if (end < 0) {
            throw new InvalidPromptException(promptId, "front matter is not closed with '---'");
        }
        FrontMatter front = readFrontMatter(promptId, text.substring(4, end));
        String body = text.substring(end + 5);

        int system = headingIndex(body, SYSTEM_HEADING);
        int user = headingIndex(body, USER_HEADING);
        if (system < 0 || user < 0 || user < system) {
            throw new InvalidPromptException(promptId, "needs '## system' followed by '## user' headings");
        }
        String systemPart = body.substring(system + SYSTEM_HEADING.length(), user).strip();
        String userPart = body.substring(user + USER_HEADING.length()).strip();
        if (systemPart.isEmpty() || userPart.isEmpty()) {
            throw new InvalidPromptException(promptId, "system and user parts must not be empty");
        }
        return new PromptTemplate(front.id(), front.purpose(), front.maxOutputTokens(), front.temperature(),
                systemPart, userPart);
    }

    private static FrontMatter readFrontMatter(String promptId, String yaml) {
        FrontMatter front;
        try {
            front = YAML.readValue(yaml, FrontMatter.class);
        } catch (IOException e) {
            throw new InvalidPromptException(promptId, "invalid front matter: " + e.getMessage(), e);
        }
        if (!promptId.equals(front.id())) {
            throw new InvalidPromptException(promptId, "front matter id '" + front.id() + "' differs from the file name");
        }
        if (front.purpose() == null || front.maxOutputTokens() == null || front.maxOutputTokens() <= 0) {
            throw new InvalidPromptException(promptId, "front matter needs purpose and a positive maxOutputTokens");
        }
        return front;
    }

    /** Index of a heading that is a whole line, or -1. */
    private static int headingIndex(String body, String heading) {
        if (body.startsWith(heading + "\n")) {
            return 0;
        }
        int index = body.indexOf("\n" + heading + "\n");
        return index < 0 ? -1 : index + 1;
    }

    record FrontMatter(String id, LlmPurpose purpose, Integer maxOutputTokens, BigDecimal temperature) {
    }
}
