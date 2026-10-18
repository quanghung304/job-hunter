package io.github.quanghung304.jobhunter.llm.registry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Which {@code LlmClient} implementation serves a provider. */
public enum ProviderType {
    ANTHROPIC("anthropic"),
    OPENAI_COMPATIBLE("openai-compatible"),
    FAKE("fake");

    private final String yamlName;

    ProviderType(String yamlName) {
        this.yamlName = yamlName;
    }

    @JsonValue
    public String yamlName() {
        return yamlName;
    }

    @JsonCreator
    public static ProviderType fromYaml(String value) {
        return Arrays.stream(values())
                .filter(type -> type.yamlName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "must be one of anthropic, openai-compatible, fake (was '" + value + "')"));
    }
}
