package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Language of the LLM one-line summaries; written in lower case in {@code profile.yaml}. */
public enum SummaryLanguage {
    @JsonProperty("vi")
    VI,
    @JsonProperty("en")
    EN;

    /** The ISO 639-1 code used in YAML and in prompts, e.g. {@code vi}. */
    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
