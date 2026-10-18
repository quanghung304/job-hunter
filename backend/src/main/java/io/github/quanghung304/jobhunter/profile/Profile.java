package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.model.Seniority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The candidate profile loaded from {@code profile.yaml} (config-spec.md §1). Immutable;
 * optional fields carry their documented defaults.
 *
 * <p>The YAML key {@code notify} maps to {@link #notifySettings()}, because a record component
 * cannot be named {@code notify}.
 */
public record Profile(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9-]+") String id,
        SummaryLanguage summaryLanguage,
        @NotNull @Valid Candidate candidate,
        @NotNull @Valid Preferences preferences,
        @NotNull @Valid Prefilter prefilter,
        @JsonProperty("notify") @Valid Notify notifySettings,
        List<@NotNull @Valid Track> tracks) {

    /** Phase 1 implements only this track; others are parsed and ignored. */
    public static final String SOFTWARE_TRACK = "software";

    public Profile {
        summaryLanguage = summaryLanguage != null ? summaryLanguage : SummaryLanguage.EN;
        notifySettings = notifySettings != null ? notifySettings : new Notify(null);
        tracks = immutable(tracks);
    }

    /** Plain-text candidate excerpt for the scoring prompt; see {@link CandidateBlock}. */
    public String candidateBlock() {
        return CandidateBlock.render(this);
    }

    public record Candidate(
            @NotBlank String headline,
            @NotNull @DecimalMin("0") BigDecimal yearsExperience,
            @NotNull @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{2}",
                    message = "must be an ISO-3166 alpha-2 code, e.g. IT") String residenceCountry,
            String city,
            @NotNull ZoneId timezone,
            @NotEmpty List<@NotNull @Valid Language> languages,
            @NotBlank String summary,
            @NotNull @Valid Skills skills,
            List<@NotBlank String> domains) {

        public Candidate {
            languages = immutable(languages);
            domains = immutable(domains);
        }
    }

    public record Language(@NotBlank String code, @NotNull LanguageLevel level) {
    }

    public record Skills(
            @NotEmpty List<@NotBlank String> core,
            List<@NotBlank String> secondary,
            List<@NotBlank String> learning) {

        public Skills {
            core = immutable(core);
            secondary = immutable(secondary);
            learning = immutable(learning);
        }
    }

    public record Preferences(
            @NotEmpty List<@NotNull EmploymentType> employmentTypes,
            @Positive @Max(168) Integer maxHoursPerWeek,
            @NotEmpty List<@NotNull @jakarta.validation.constraints.Pattern(
                    regexp = RegionMatcher.TOKEN_REGEX,
                    message = "must be a region token or ISO-3166 alpha-2 code (config-spec §1.1)")
                    String> acceptRegions,
            @Valid TimezonePreference timezone,
            @Valid Salary minSalary,
            @NotEmpty List<@NotNull Seniority> seniority,
            List<@NotBlank String> excludeCompanies) {

        public Preferences {
            employmentTypes = immutable(employmentTypes);
            acceptRegions = immutable(acceptRegions);
            timezone = timezone != null ? timezone : new TimezonePreference(null);
            seniority = immutable(seniority);
            excludeCompanies = immutable(excludeCompanies);
        }
    }

    public record TimezonePreference(@Min(0) @Max(26) Integer maxOffsetDiffHours) {

        public static final int DEFAULT_MAX_OFFSET_DIFF_HOURS = 6;

        public TimezonePreference {
            maxOffsetDiffHours = maxOffsetDiffHours != null ? maxOffsetDiffHours : DEFAULT_MAX_OFFSET_DIFF_HOURS;
        }
    }

    public record Salary(
            @NotNull @Positive BigDecimal amount,
            @NotNull @jakarta.validation.constraints.Pattern(regexp = "[A-Z]{3}",
                    message = "must be an ISO-4217 code, e.g. EUR") String currency,
            @NotNull SalaryPeriod period) {
    }

    /** Title regexes are compiled case-insensitively while loading. */
    public record Prefilter(
            @Positive Integer maxAgeDays,
            @NotEmpty @JsonDeserialize(contentUsing = CaseInsensitivePatternDeserializer.class)
            List<@NotNull Pattern> titleInclude,
            @JsonDeserialize(contentUsing = CaseInsensitivePatternDeserializer.class)
            List<@NotNull Pattern> titleExclude,
            @Positive Integer minSkillHits) {

        public static final int DEFAULT_MAX_AGE_DAYS = 21;
        public static final int DEFAULT_MIN_SKILL_HITS = 2;

        public Prefilter {
            maxAgeDays = maxAgeDays != null ? maxAgeDays : DEFAULT_MAX_AGE_DAYS;
            titleInclude = immutable(titleInclude);
            titleExclude = immutable(titleExclude);
            minSkillHits = minSkillHits != null ? minSkillHits : DEFAULT_MIN_SKILL_HITS;
        }
    }

    /**
     * Telegram settings: the daily digest only. {@code alertMinScore} belonged to the removed
     * instant alerts (ADR-0010); it is ignored, not rejected, so older profile files still load.
     */
    @JsonIgnoreProperties({"alertMinScore"})
    public record Notify(@Min(0) @Max(100) Integer digestMinScore) {

        public static final int DEFAULT_DIGEST_MIN_SCORE = 65;

        public Notify {
            digestMinScore = digestMinScore != null ? digestMinScore : DEFAULT_DIGEST_MIN_SCORE;
        }
    }

    /** Only {@code name} and {@code enabled} are read; track-specific keys are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Track(@NotBlank String name, Boolean enabled) {

        public Track {
            enabled = enabled != null ? enabled : Boolean.TRUE;
        }
    }

    /** Unmodifiable copy; {@code null} becomes empty. Null elements are kept for validation. */
    private static <T> List<T> immutable(List<T> list) {
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }
}
