package io.github.quanghung304.jobhunter.llm.prompt;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One versioned prompt file (docs/ai/prompts/README.md): front matter plus {@code ## system}
 * and {@code ## user} parts with {@code {{name}}} placeholders.
 */
public record PromptTemplate(
        String id,
        LlmPurpose purpose,
        int maxOutputTokens,
        BigDecimal temperature,
        String system,
        String user) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*}}");

    /** Placeholder names used by both parts, in order of first use. */
    public Set<String> placeholders() {
        Set<String> names = new LinkedHashSet<>();
        for (String part : List.of(system, user)) {
            Matcher matcher = PLACEHOLDER.matcher(part);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        return names;
    }

    /**
     * Fills every placeholder. Fails if any placeholder has no value (no silent blanks); extra
     * variables are ignored. {@code </} in values becomes {@code <\/} so a value cannot close
     * the XML-style tag it is placed in.
     */
    public RenderedPrompt render(Map<String, String> variables) {
        List<String> missing = new ArrayList<>();
        for (String name : placeholders()) {
            if (variables.get(name) == null) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingPlaceholderException(id, missing);
        }
        return new RenderedPrompt(id, fill(system, variables), fill(user, variables), maxOutputTokens, temperature);
    }

    private static String fill(String text, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1)).replace("</", "<\\/");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
