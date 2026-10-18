package io.github.quanghung304.jobhunter.llm.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PromptRepositoryTest {

    final PromptRepository repository = new PromptRepository();

    @Test
    void loadsTheRealExtractPrompt() {
        PromptTemplate prompt = repository.get("extract-v1");

        assertThat(prompt.id()).isEqualTo("extract-v1");
        assertThat(prompt.purpose()).isEqualTo(LlmPurpose.EXTRACT);
        assertThat(prompt.maxOutputTokens()).isEqualTo(1200);
        assertThat(prompt.temperature()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(prompt.system()).startsWith("You extract structured facts").doesNotContain("## user");
        assertThat(prompt.user()).startsWith("<job>").endsWith("Return the JSON object.");
        assertThat(prompt.placeholders()).containsExactly(
                "title", "company", "locationText", "remoteHint", "employmentHint", "description");
    }

    @Test
    void loadsTheRealScorePrompt() {
        PromptTemplate prompt = repository.get("score-v1");

        assertThat(prompt.purpose()).isEqualTo(LlmPurpose.SCORE);
        assertThat(prompt.maxOutputTokens()).isEqualTo(800);
        assertThat(prompt.placeholders()).containsExactly("summaryLanguage", "candidateBlock", "title", "company",
                "extractionJson", "eligibility", "eligibilityReasons", "description");
    }

    @Test
    void cachesTemplates() {
        assertThat(repository.get("score-v1")).isSameAs(repository.get("score-v1"));
    }

    @Test
    void unknownOrMalformedIdsFail() {
        assertThatThrownBy(() -> repository.get("extract-v99")).isInstanceOf(InvalidPromptException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> repository.get("../secrets")).isInstanceOf(InvalidPromptException.class)
                .hasMessageContaining("<purpose>-v<n>");
    }

    @Test
    void parseHandlesWindowsLineEndings() {
        PromptTemplate prompt = PromptRepository.parse("test-v1",
                "---\r\nid: test-v1\r\npurpose: SCORE\r\nmaxOutputTokens: 10\r\n---\r\n## system\r\nS {{a}}\r\n\r\n## user\r\nU {{b}}\r\n");

        assertThat(prompt.system()).isEqualTo("S {{a}}");
        assertThat(prompt.user()).isEqualTo("U {{b}}");
        assertThat(prompt.temperature()).isNull();
    }

    @Test
    void parseRejectsBrokenFiles() {
        assertThatThrownBy(() -> PromptRepository.parse("t-v1", "## system\nx\n## user\ny"))
                .isInstanceOf(InvalidPromptException.class).hasMessageContaining("front matter");
        assertThatThrownBy(() -> PromptRepository.parse("t-v1",
                "---\nid: other-v1\npurpose: SCORE\nmaxOutputTokens: 1\n---\n## system\nx\n## user\ny"))
                .isInstanceOf(InvalidPromptException.class).hasMessageContaining("differs from the file name");
        assertThatThrownBy(() -> PromptRepository.parse("t-v1",
                "---\nid: t-v1\npurpose: SCORE\nmaxOutputTokens: 1\n---\n## user\ny\n## system\nx"))
                .isInstanceOf(InvalidPromptException.class).hasMessageContaining("'## system' followed by '## user'");
        assertThatThrownBy(() -> PromptRepository.parse("t-v1",
                "---\nid: t-v1\npurpose: SCORE\n---\n## system\nx\n## user\ny"))
                .isInstanceOf(InvalidPromptException.class).hasMessageContaining("maxOutputTokens");
    }
}
