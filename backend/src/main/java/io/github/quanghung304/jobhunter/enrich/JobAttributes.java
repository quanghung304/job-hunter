package io.github.quanghung304.jobhunter.enrich;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.model.Seniority;
import io.github.quanghung304.jobhunter.profile.RegionMatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Attributes the LLM extracts from one posting (scoring-spec.md §3), profile-independent.
 * Unknown JSON fields are ignored. Lenient where it costs nothing: missing lists become empty,
 * region tokens and language codes are upper-/lower-cased, and over-long free text is cut to
 * its limit instead of failing the extraction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobAttributes(
        @NotNull RemoteType remoteType,
        List<@NotNull @Pattern(regexp = RegionMatcher.TOKEN_REGEX,
                message = "must be a region token or ISO-3166 alpha-2 code") String> allowedRegions,
        String regionEvidence,
        @Valid TimezoneRequirement timezoneRequirement,
        List<@NotNull EmploymentType> employmentTypes,
        @Valid HoursPerWeek hoursPerWeek,
        Seniority seniority,
        @PositiveOrZero @Max(60) Integer minYearsExperience,
        @Size(max = 15) List<@NotBlank String> requiredSkills,
        @Size(max = 15) List<@NotBlank String> niceToHaveSkills,
        List<@NotNull @Valid RequiredLanguage> requiredLanguages,
        String workAuthorization,
        @Valid Salary salary,
        @NotBlank String roleSummary,
        @NotNull Boolean isAgencyOrReposting,
        @NotNull Boolean isScamSignal,
        @NotNull Confidence confidence) {

    public static final int EVIDENCE_MAX = 200;
    public static final int WORK_AUTHORIZATION_MAX = 200;
    public static final int ROLE_SUMMARY_MAX = 300;

    public JobAttributes {
        allowedRegions = copy(allowedRegions).stream().map(r -> r.strip().toUpperCase(Locale.ROOT)).toList();
        regionEvidence = cut(regionEvidence, EVIDENCE_MAX);
        employmentTypes = copy(employmentTypes);
        requiredSkills = copy(requiredSkills);
        niceToHaveSkills = copy(niceToHaveSkills);
        requiredLanguages = copy(requiredLanguages);
        workAuthorization = cut(workAuthorization, WORK_AUTHORIZATION_MAX);
        roleSummary = cut(roleSummary, ROLE_SUMMARY_MAX);
    }

    public enum RemoteType {
        FULLY_REMOTE,
        REMOTE_RESTRICTED,
        HYBRID,
        ONSITE,
        UNKNOWN
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /** UTC offsets in hours; {@code overlapHours} may be null. */
    public record TimezoneRequirement(
            @NotNull @Min(-12) @Max(14) Integer minUtcOffset,
            @NotNull @Min(-12) @Max(14) Integer maxUtcOffset,
            @Min(0) @Max(24) Integer overlapHours) {
    }

    public record HoursPerWeek(@Min(0) @Max(168) Integer min, @Min(0) @Max(168) Integer max) {
    }

    /** ISO-639-1 code and CEFR level ({@code NATIVE} above {@code C2}). */
    public record RequiredLanguage(
            @NotNull @Pattern(regexp = "[a-z]{2}", message = "must be an ISO-639-1 code, e.g. de") String code,
            @NotNull LanguageLevel level) {

        public RequiredLanguage {
            code = code == null ? null : code.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record Salary(
            @PositiveOrZero BigDecimal min,
            @PositiveOrZero BigDecimal max,
            @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code, e.g. EUR") String currency,
            SalaryPeriod period) {
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private static String cut(String text, int max) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max - 1).stripTrailing() + "…";
    }
}
