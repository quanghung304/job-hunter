package io.github.quanghung304.jobhunter.enrich;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds which skills of a list a text mentions (scoring-spec.md §2, {@code NOT_RELEVANT}).
 * Matching is case-insensitive and on word boundaries ("Java" does not match "JavaScript").
 * Each skill also matches its variants:
 * <ul>
 *   <li>words joined by space, hyphen, underscore or nothing ("Spring Boot" ~ "SpringBoot" ~ "spring-boot");
 *   <li>the parts of a slash name when every part has 3+ characters ("Hibernate/JPA" ~ "JPA",
 *       but "CI/CD" stays whole);
 *   <li>the aliases in {@link #ALIASES}.
 * </ul>
 */
public final class SkillMatcher {

    /** Extra spellings, keyed by the lower-case skill name with single spaces. */
    static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("rest api", List.of("RESTful", "REST APIs")),
            Map.entry("rest", List.of("RESTful")),
            Map.entry("oauth 2.0", List.of("OAuth2", "OAuth 2", "OAuth")),
            Map.entry("oauth2", List.of("OAuth 2.0", "OAuth 2", "OAuth")),
            Map.entry("openid connect", List.of("OIDC")),
            Map.entry("iam/sso", List.of("identity and access management", "single sign-on")),
            Map.entry("iam", List.of("identity and access management")),
            Map.entry("sso", List.of("single sign-on")),
            Map.entry("microservices", List.of("microservice", "micro-services", "micro-service")),
            Map.entry("hibernate/jpa", List.of("Spring Data JPA")),
            Map.entry("apache kafka", List.of("Kafka")),
            Map.entry("kafka", List.of("Apache Kafka")),
            Map.entry("postgresql", List.of("Postgres")),
            Map.entry("kubernetes", List.of("k8s")),
            Map.entry("node.js", List.of("NodeJS")),
            Map.entry("ci/cd", List.of("continuous integration", "continuous delivery", "continuous deployment")),
            Map.entry("amazon web services", List.of("AWS")),
            Map.entry("aws", List.of("Amazon Web Services")),
            Map.entry("google cloud", List.of("GCP")),
            Map.entry("gcp", List.of("Google Cloud")),
            Map.entry("s3", List.of("Amazon S3", "AWS S3")));

    private static final Pattern WORD_SEPARATORS = Pattern.compile("[\\s_-]+");
    private static final String SEPARATOR = "[\\s_-]?";
    private static final String NOT_AFTER_WORD = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_BEFORE_WORD = "(?![\\p{L}\\p{N}])";
    private static final int MIN_SLASH_PART_LENGTH = 3;

    private final Map<String, Pattern> patterns;

    private SkillMatcher(Map<String, Pattern> patterns) {
        this.patterns = patterns;
    }

    /** A matcher for these skills; blank entries are ignored, duplicates (ignoring case) kept once. */
    public static SkillMatcher of(List<String> skills) {
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String skill : skills) {
            if (skill == null || skill.isBlank() || !seen.add(key(skill))) {
                continue;
            }
            patterns.put(skill.strip(), pattern(skill));
        }
        return new SkillMatcher(patterns);
    }

    /** The skills (as given) found in {@code text}, in list order. */
    public Set<String> hits(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return patterns.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(text).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** All spellings that count as {@code skill}, the skill itself first. */
    static List<String> variants(String skill) {
        String stripped = skill.strip();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(stripped);
        if (stripped.contains("/")) {
            List<String> parts = Arrays.stream(stripped.split("/")).map(String::strip).toList();
            if (parts.stream().allMatch(part -> part.length() >= MIN_SLASH_PART_LENGTH)) {
                variants.addAll(parts);
            }
        }
        List<String> sources = new ArrayList<>(variants);
        for (String source : sources) {
            variants.addAll(ALIASES.getOrDefault(key(source), List.of()));
        }
        return List.copyOf(variants);
    }

    static Pattern pattern(String skill) {
        String alternatives = variants(skill).stream()
                .map(SkillMatcher::variantRegex)
                .collect(Collectors.joining("|"));
        return Pattern.compile(NOT_AFTER_WORD + "(?:" + alternatives + ")" + NOT_BEFORE_WORD,
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String variantRegex(String variant) {
        return Arrays.stream(WORD_SEPARATORS.split(variant.strip()))
                .map(Pattern::quote)
                .collect(Collectors.joining(SEPARATOR));
    }

    private static String key(String skill) {
        return WORD_SEPARATORS.matcher(skill.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
