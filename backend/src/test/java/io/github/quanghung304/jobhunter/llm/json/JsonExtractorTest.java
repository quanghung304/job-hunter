package io.github.quanghung304.jobhunter.llm.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonExtractorTest {

    record Answer(@NotBlank String summary, @NotNull @Min(0) @Max(100) Integer score, List<String> gaps) {
    }

    final JsonExtractor extractor = new JsonExtractor(Validation.buildDefaultValidatorFactory().getValidator());

    static Stream<Arguments> validAnswers() {
        return Stream.of(
                Arguments.of("plain object", "{\"summary\":\"ok\",\"score\":7}"),
                Arguments.of("prose before and after", "Here you go: {\"summary\":\"ok\",\"score\":7} Hope it helps {x}"),
                Arguments.of("markdown fence", "```json\n{\"summary\":\"ok\",\"score\":7}\n```"),
                Arguments.of("braces inside strings", "{\"summary\":\"a } b { c\",\"score\":7}"),
                Arguments.of("escaped quotes in strings", "{\"summary\":\"say \\\"}\\\" now\",\"score\":7}"),
                Arguments.of("invalid brace text first", "{not json} then {\"summary\":\"ok\",\"score\":7}"),
                Arguments.of("unknown fields ignored", "{\"summary\":\"ok\",\"score\":7,\"extra\":{\"a\":1}}"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validAnswers")
    void extractsTheFirstValidObject(String name, String text) {
        Answer answer = extractor.extract(text, Answer.class);

        assertThat(answer.score()).isEqualTo(7);
        assertThat(answer.summary()).isNotBlank();
    }

    @Test
    void nestedObjectsStayInside() {
        assertThat(JsonExtractor.firstObject("x {\"a\":{\"b\":{}},\"c\":1} y")).contains("{\"a\":{\"b\":{}},\"c\":1}");
    }

    @Test
    void noObject() {
        assertThatThrownBy(() -> extractor.extract("I cannot help with that.", Answer.class))
                .isInstanceOf(InvalidLlmOutputException.class).hasMessage("The answer contains no JSON object.");
        assertThatThrownBy(() -> extractor.extract("{\"summary\": \"cut off", Answer.class))
                .isInstanceOf(InvalidLlmOutputException.class);
        assertThatThrownBy(() -> extractor.extract(null, Answer.class)).isInstanceOf(InvalidLlmOutputException.class);
    }

    @Test
    void wrongTypesAreReported() {
        assertThatThrownBy(() -> extractor.extract("{\"summary\":\"ok\",\"score\":\"high\"}", Answer.class))
                .isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageStartingWith("The JSON object does not match the expected shape");
    }

    @Test
    void validationErrorsAreListedForTheCorrectiveRetry() {
        assertThatThrownBy(() -> extractor.extract("{\"summary\":\"\",\"score\":120}", Answer.class))
                .isInstanceOf(InvalidLlmOutputException.class)
                .hasMessage("The JSON object is invalid: score must be less than or equal to 100; summary must not be blank");
    }
}
