package io.github.quanghung304.jobhunter.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import java.util.Collection;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Writes a profile as {@code profile.yaml} in canonical form (ADR-0012): keys in config-spec §1
 * order, absent optional keys and empty optional lists left out (no nulls), defaults written as
 * values, regexes as their source text, multi-line text as a literal block. Comments are not
 * kept. {@code tracks} is written as given (raw), so track-specific keys of the file survive.
 */
final class ProfileYamlWriter {

    static final String HEADER = "# Saved from the Profile page (ADR-0012); the previous file is profile.yaml.bak.\n";

    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
            .build());

    private ProfileYamlWriter() {
    }

    /**
     * @param tracks the {@code tracks} node to write as is, or null to write the parsed tracks
     *               ({@code name}, {@code enabled})
     */
    static String write(Profile profile, JsonNode tracks) {
        ObjectNode root = YAML.createObjectNode();
        root.put("id", profile.id());
        root.put("summaryLanguage", profile.summaryLanguage().code());

        Profile.Candidate c = profile.candidate();
        ObjectNode candidate = root.putObject("candidate");
        candidate.put("headline", c.headline());
        candidate.put("yearsExperience", c.yearsExperience());
        candidate.put("residenceCountry", c.residenceCountry());
        putIfPresent(candidate, "city", c.city());
        candidate.put("timezone", c.timezone().getId());
        ArrayNode languages = candidate.putArray("languages");
        c.languages().forEach(l -> languages.addObject().put("code", l.code()).put("level", l.level().name()));
        candidate.put("summary", c.summary());
        ObjectNode skills = candidate.putObject("skills");
        putList(skills, "core", c.skills().core(), Function.identity());
        putListIfAny(skills, "secondary", c.skills().secondary(), Function.identity());
        putListIfAny(skills, "learning", c.skills().learning(), Function.identity());
        putListIfAny(candidate, "domains", c.domains(), Function.identity());

        Profile.Preferences p = profile.preferences();
        ObjectNode preferences = root.putObject("preferences");
        putList(preferences, "employmentTypes", p.employmentTypes(), Enum::name);
        if (p.maxHoursPerWeek() != null) {
            preferences.put("maxHoursPerWeek", p.maxHoursPerWeek());
        }
        putList(preferences, "acceptRegions", p.acceptRegions(), Function.identity());
        preferences.putObject("timezone").put("maxOffsetDiffHours", p.timezone().maxOffsetDiffHours());
        if (p.minSalary() != null) {
            preferences.putObject("minSalary")
                    .put("amount", p.minSalary().amount())
                    .put("currency", p.minSalary().currency())
                    .put("period", p.minSalary().period().name());
        }
        putList(preferences, "seniority", p.seniority(), Enum::name);
        putListIfAny(preferences, "excludeCompanies", p.excludeCompanies(), Function.identity());

        Profile.Prefilter f = profile.prefilter();
        ObjectNode prefilter = root.putObject("prefilter");
        prefilter.put("maxAgeDays", f.maxAgeDays());
        putList(prefilter, "titleInclude", f.titleInclude(), Pattern::pattern);
        putListIfAny(prefilter, "titleExclude", f.titleExclude(), Pattern::pattern);
        prefilter.put("minSkillHits", f.minSkillHits());

        root.putObject("notify").put("digestMinScore", profile.notifySettings().digestMinScore());

        if (tracks != null && !tracks.isNull() && !tracks.isMissingNode() && !tracks.isEmpty()) {
            root.set("tracks", tracks.deepCopy());
        } else if (!profile.tracks().isEmpty()) {
            ArrayNode written = root.putArray("tracks");
            profile.tracks().forEach(t -> written.addObject().put("name", t.name()).put("enabled", t.enabled()));
        }

        try {
            return HEADER + YAML.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot write the profile as YAML", e);
        }
    }

    private static void putIfPresent(ObjectNode node, String key, String value) {
        if (value != null && !value.isBlank()) {
            node.put(key, value);
        }
    }

    private static <T> void putList(ObjectNode node, String key, Collection<T> values, Function<T, String> text) {
        ArrayNode array = node.putArray(key);
        values.forEach(value -> array.add(text.apply(value)));
    }

    private static <T> void putListIfAny(ObjectNode node, String key, Collection<T> values, Function<T, String> text) {
        if (!values.isEmpty()) {
            putList(node, key, values, text);
        }
    }
}
