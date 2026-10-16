package io.github.quanghung304.jobhunter.profile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The region rule of config-spec.md §1.1: may a candidate living in {@code residenceCountry}
 * and accepting {@code acceptRegions} take a job restricted to {@code allowedRegions}?
 *
 * <p>Membership is known for {@code EU}, {@code EEA}, {@code EUROPE} and {@code EMEA}
 * (EMEA ⊇ EUROPE ⊇ EEA ⊇ EU). Other region tokens ({@code AMERICAS}, {@code LATAM},
 * {@code APAC}, ...) only match when listed in {@code acceptRegions}.
 */
public final class RegionMatcher {

    /** Region tokens and ISO-3166 alpha-2 codes accepted in profile and extraction output. */
    public static final String TOKEN_REGEX =
            "WORLDWIDE|EU|EEA|EUROPE|EMEA|AMERICAS|NORTH_AMERICA|LATAM|APAC|ASIA|[A-Z]{2}";

    public static final String WORLDWIDE = "WORLDWIDE";

    static final Set<String> EU = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
            "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE");

    static final Set<String> EEA = union(EU, Set.of("IS", "LI", "NO"));

    /** Geographic Europe: EEA plus the other European states. */
    static final Set<String> EUROPE = union(EEA, Set.of(
            "AD", "AL", "BA", "BY", "CH", "GB", "MC", "MD", "ME", "MK", "RS", "RU", "SM", "UA",
            "VA", "XK"));

    static final Set<String> MIDDLE_EAST = Set.of(
            "AE", "AM", "AZ", "BH", "GE", "IL", "IQ", "IR", "JO", "KW", "LB", "OM", "PS", "QA",
            "SA", "SY", "TR", "YE");

    static final Set<String> AFRICA = Set.of(
            "AO", "BF", "BI", "BJ", "BW", "CD", "CF", "CG", "CI", "CM", "CV", "DJ", "DZ", "EG",
            "ER", "ET", "GA", "GH", "GM", "GN", "GQ", "GW", "KE", "KM", "LR", "LS", "LY", "MA",
            "MG", "ML", "MR", "MU", "MW", "MZ", "NA", "NE", "NG", "RW", "SC", "SD", "SL", "SN",
            "SO", "SS", "ST", "SZ", "TD", "TG", "TN", "TZ", "UG", "ZA", "ZM", "ZW");

    static final Set<String> EMEA = union(EUROPE, MIDDLE_EAST, AFRICA);

    private static final Map<String, Set<String>> MEMBERS = Map.of(
            "EU", EU, "EEA", EEA, "EUROPE", EUROPE, "EMEA", EMEA);

    private final String residenceCountry;
    private final Set<String> acceptRegions;

    public RegionMatcher(String residenceCountry, List<String> acceptRegions) {
        this.residenceCountry = normalize(Objects.requireNonNull(residenceCountry, "residenceCountry"));
        this.acceptRegions = acceptRegions.stream().map(RegionMatcher::normalize).collect(Collectors.toUnmodifiableSet());
    }

    public static RegionMatcher of(Profile profile) {
        return new RegionMatcher(profile.candidate().residenceCountry(), profile.preferences().acceptRegions());
    }

    /**
     * {@code UNKNOWN} when no region is stated; {@code OK} when any stated region is
     * {@code WORLDWIDE}, is accepted, or contains the residence country; otherwise {@code FAIL}.
     */
    public RegionVerdict isAccepted(List<String> allowedRegions) {
        if (allowedRegions == null || allowedRegions.stream().allMatch(r -> r == null || r.isBlank())) {
            return RegionVerdict.UNKNOWN;
        }
        boolean ok = allowedRegions.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(RegionMatcher::normalize)
                .anyMatch(this::isOk);
        return ok ? RegionVerdict.OK : RegionVerdict.FAIL;
    }

    private boolean isOk(String region) {
        return region.equals(WORLDWIDE)
                || acceptRegions.contains(region)
                || region.equals(residenceCountry)
                || MEMBERS.getOrDefault(region, Set.of()).contains(residenceCountry);
    }

    private static String normalize(String token) {
        return token.strip().toUpperCase(Locale.ROOT);
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        var all = new HashSet<String>();
        Stream.of(sets).forEach(all::addAll);
        return Set.copyOf(all);
    }
}
