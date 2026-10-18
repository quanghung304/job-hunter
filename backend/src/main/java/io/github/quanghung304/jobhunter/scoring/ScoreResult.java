package io.github.quanghung304.jobhunter.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * The scoring answer of scoring-spec.md §5. Ranges and list sizes are validated (a violation
 * triggers the corrective retry); over-long text is cut instead of failing. {@code score} is
 * not trusted: {@link #withSumAsScore()} replaces it with the sum of the breakdown.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreResult(
        @NotNull @Valid Breakdown breakdown,
        Integer score,
        @Size(max = 3) List<@NotBlank String> strengths,
        @Size(max = 3) List<@NotBlank String> gaps,
        @Size(max = 3) List<@NotBlank String> redFlags,
        @NotBlank String summary,
        String applyAdvice) {

    public static final int STRENGTH_MAX = 120;
    public static final int SUMMARY_MAX = 200;
    public static final int APPLY_ADVICE_MAX = 200;

    public ScoreResult {
        strengths = copy(strengths).stream().map(s -> cut(s, STRENGTH_MAX)).toList();
        gaps = copy(gaps);
        redFlags = copy(redFlags);
        summary = cut(summary, SUMMARY_MAX);
        applyAdvice = cut(applyAdvice, APPLY_ADVICE_MAX);
    }

    /** Points per rubric key; the weights sum to 100. */
    public record Breakdown(
            @NotNull @Min(0) @Max(40) Integer skills,
            @NotNull @Min(0) @Max(20) Integer experience,
            @NotNull @Min(0) @Max(20) Integer arrangement,
            @NotNull @Min(0) @Max(10) Integer domain,
            @NotNull @Min(0) @Max(10) Integer other) {

        public int sum() {
            return skills + experience + arrangement + domain + other;
        }
    }

    public boolean scoreMatchesBreakdown() {
        return score != null && score == breakdown.sum();
    }

    /** This result with {@code score} = the sum of the breakdown. */
    public ScoreResult withSumAsScore() {
        return new ScoreResult(breakdown, breakdown.sum(), strengths, gaps, redFlags, summary, applyAdvice);
    }

    private static List<String> copy(List<String> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).map(String::strip).toList();
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max - 1).stripTrailing() + "…";
    }
}
