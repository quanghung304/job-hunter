package io.github.quanghung304.jobhunter.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.model.Seniority;
import io.github.quanghung304.jobhunter.profile.Profile;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The profile as the API shows and accepts it: the keys and structure of {@code profile.yaml}
 * (config-spec §1), regexes as text. {@code tracks} is not part of it: the file's tracks are kept
 * on save. On {@code PUT} a missing (null) optional value means "default", as in the YAML file;
 * the profile module validates everything with the startup rules.
 */
@Schema(name = "Profile")
public record ProfileDto(
        @Schema(requiredMode = REQUIRED) String id,
        @Schema(requiredMode = REQUIRED, allowableValues = {"vi", "en"}) String summaryLanguage,
        @Schema(requiredMode = REQUIRED) Candidate candidate,
        @Schema(requiredMode = REQUIRED) Preferences preferences,
        @Schema(requiredMode = REQUIRED) Prefilter prefilter,
        @Schema(requiredMode = REQUIRED) @JsonProperty("notify") Notify notifySettings) {

    @Schema(name = "ProfileCandidate")
    public record Candidate(
            @Schema(requiredMode = REQUIRED) String headline,
            @Schema(requiredMode = REQUIRED) BigDecimal yearsExperience,
            @Schema(requiredMode = REQUIRED, description = "ISO-3166 alpha-2") String residenceCountry,
            @Schema(nullable = true) String city,
            @Schema(requiredMode = REQUIRED, description = "IANA time zone, e.g. Europe/Rome") String timezone,
            @Schema(requiredMode = REQUIRED) List<Language> languages,
            @Schema(requiredMode = REQUIRED) String summary,
            @Schema(requiredMode = REQUIRED) Skills skills,
            @Schema(requiredMode = REQUIRED) List<String> domains) {
    }

    @Schema(name = "ProfileLanguage")
    public record Language(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) LanguageLevel level) {
    }

    @Schema(name = "ProfileSkills")
    public record Skills(
            @Schema(requiredMode = REQUIRED) List<String> core,
            @Schema(requiredMode = REQUIRED) List<String> secondary,
            @Schema(requiredMode = REQUIRED) List<String> learning) {
    }

    @Schema(name = "ProfilePreferences")
    public record Preferences(
            @Schema(requiredMode = REQUIRED) List<EmploymentType> employmentTypes,
            @Schema(nullable = true) Integer maxHoursPerWeek,
            @Schema(requiredMode = REQUIRED, description = "Region tokens (config-spec §1.1)") List<String> acceptRegions,
            @Schema(requiredMode = REQUIRED) Timezone timezone,
            @Schema(nullable = true) Salary minSalary,
            @Schema(requiredMode = REQUIRED) List<Seniority> seniority,
            @Schema(requiredMode = REQUIRED) List<String> excludeCompanies) {
    }

    @Schema(name = "ProfileTimezone")
    public record Timezone(@Schema(requiredMode = REQUIRED) Integer maxOffsetDiffHours) {
    }

    @Schema(name = "ProfileSalary")
    public record Salary(
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED, description = "ISO-4217, e.g. EUR") String currency,
            @Schema(requiredMode = REQUIRED) SalaryPeriod period) {
    }

    @Schema(name = "ProfilePrefilter")
    public record Prefilter(
            @Schema(requiredMode = REQUIRED) Integer maxAgeDays,
            @Schema(requiredMode = REQUIRED, description = "Case-insensitive regexes") List<String> titleInclude,
            @Schema(requiredMode = REQUIRED, description = "Case-insensitive regexes") List<String> titleExclude,
            @Schema(requiredMode = REQUIRED) Integer minSkillHits) {
    }

    @Schema(name = "ProfileNotify")
    public record Notify(@Schema(requiredMode = REQUIRED) Integer digestMinScore) {
    }

    static ProfileDto of(Profile profile) {
        Profile.Candidate c = profile.candidate();
        Profile.Preferences p = profile.preferences();
        Profile.Prefilter f = profile.prefilter();
        return new ProfileDto(
                profile.id(),
                profile.summaryLanguage().code(),
                new Candidate(c.headline(), c.yearsExperience(), c.residenceCountry(), c.city(), c.timezone().getId(),
                        c.languages().stream().map(l -> new Language(l.code(), l.level())).toList(), c.summary(),
                        new Skills(c.skills().core(), c.skills().secondary(), c.skills().learning()), c.domains()),
                new Preferences(p.employmentTypes(), p.maxHoursPerWeek(), p.acceptRegions(),
                        new Timezone(p.timezone().maxOffsetDiffHours()),
                        p.minSalary() == null ? null
                                : new Salary(p.minSalary().amount(), p.minSalary().currency(), p.minSalary().period()),
                        p.seniority(), p.excludeCompanies()),
                new Prefilter(f.maxAgeDays(), f.titleInclude().stream().map(Pattern::pattern).toList(),
                        f.titleExclude().stream().map(Pattern::pattern).toList(), f.minSkillHits()),
                new Notify(profile.notifySettings().digestMinScore()));
    }
}
