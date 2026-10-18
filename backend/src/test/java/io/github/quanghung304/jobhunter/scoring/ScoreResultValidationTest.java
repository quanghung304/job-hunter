package io.github.quanghung304.jobhunter.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.llm.json.InvalidLlmOutputException;
import io.github.quanghung304.jobhunter.llm.json.JsonExtractor;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** ScoreResult as the router parses model answers (scoring-spec §5). */
class ScoreResultValidationTest {

    static final String VALID = """
            {"breakdown":{"skills":34,"experience":15,"arrangement":14,"domain":6,"other":6},"score":75,
             "strengths":["Java and Spring Boot"],"gaps":["No Kafka"],"redFlags":[],
             "summary":"Phù hợp tốt.","applyAdvice":"Nhấn mạnh Spring Boot.","extra":1}""";

    final JsonExtractor extractor = new JsonExtractor(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validAnswer() {
        ScoreResult result = extractor.extract(VALID, ScoreResult.class);

        assertThat(result.breakdown().sum()).isEqualTo(75);
        assertThat(result.scoreMatchesBreakdown()).isTrue();
        assertThat(result.strengths()).containsExactly("Java and Spring Boot");
        assertThat(result.redFlags()).isEmpty();
        assertThat(result.summary()).isEqualTo("Phù hợp tốt.");
    }

    @Test
    void wrongSumIsReplacedNotRejected() {
        ScoreResult result = extractor.extract(VALID.replace("\"score\":75", "\"score\":99"), ScoreResult.class);

        assertThat(result.scoreMatchesBreakdown()).isFalse();
        assertThat(result.withSumAsScore().score()).isEqualTo(75);
        assertThat(extractor.extract(VALID.replace("\"score\":75,", ""), ScoreResult.class).withSumAsScore().score())
                .as("missing score").isEqualTo(75);
    }

    @Test
    void longTextIsCutNotRejected() {
        String answer = VALID
                .replace("\"Phù hợp tốt.\"", "\"" + "s".repeat(300) + "\"")
                .replace("\"Nhấn mạnh Spring Boot.\"", "\"" + "a".repeat(260) + "\"")
                .replace("\"Java and Spring Boot\"", "\"" + "k".repeat(150) + "\"");

        ScoreResult result = extractor.extract(answer, ScoreResult.class);

        assertThat(result.summary()).hasSize(ScoreResult.SUMMARY_MAX).endsWith("…");
        assertThat(result.applyAdvice()).hasSize(ScoreResult.APPLY_ADVICE_MAX);
        assertThat(result.strengths().get(0)).hasSize(ScoreResult.STRENGTH_MAX);
    }

    @Test
    void missingListsBecomeEmpty() {
        ScoreResult result = extractor.extract("""
                {"breakdown":{"skills":1,"experience":1,"arrangement":1,"domain":1,"other":1},"score":5,
                 "summary":"ok"}""", ScoreResult.class);

        assertThat(result.strengths()).isEmpty();
        assertThat(result.gaps()).isEmpty();
        assertThat(result.redFlags()).isEmpty();
        assertThat(result.applyAdvice()).isNull();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            skills above 40       | "skills":34          | "skills":41          | breakdown.skills
            experience below 0    | "experience":15      | "experience":-1      | breakdown.experience
            arrangement above 20  | "arrangement":14     | "arrangement":21     | breakdown.arrangement
            domain above 10       | "domain":6           | "domain":11          | breakdown.domain
            other missing         | ,"other":6           | ''                   | breakdown.other
            four strengths        | ["Java and Spring Boot"] | ["a","b","c","d"] | strengths
            blank summary         | "Phù hợp tốt."       | "  "                 | summary
            """)
    void invalidAnswersAreRejected(String name, String from, String to, String field) {
        String answer = VALID.replace(from, "''".equals(to) ? "" : to);

        assertThatThrownBy(() -> extractor.extract(answer, ScoreResult.class))
                .isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining(field);
    }

    @Test
    void breakdownMissing() {
        assertThatThrownBy(() -> extractor.extract("{\"score\":50,\"summary\":\"x\"}", ScoreResult.class))
                .isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining("breakdown");
    }
}
