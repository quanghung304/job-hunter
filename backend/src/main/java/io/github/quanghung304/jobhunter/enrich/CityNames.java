package io.github.quanghung304.jobhunter.enrich;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recognizes a city in a free-text location, also under its local or English name
 * ("Turin" ~ "Torino", "Munich" ~ "München"). Case- and accent-insensitive, whole words.
 */
final class CityNames {

    /** Names of the same city; folded (lower-case, no accents). Extend when needed. */
    static final List<List<String>> SAME_CITY = List.of(
            List.of("turin", "torino"),
            List.of("milan", "milano"),
            List.of("rome", "roma"),
            List.of("naples", "napoli"),
            List.of("florence", "firenze"),
            List.of("venice", "venezia"),
            List.of("genoa", "genova"),
            List.of("padua", "padova"),
            List.of("munich", "munchen", "muenchen"),
            List.of("cologne", "koln", "koeln"),
            List.of("nuremberg", "nurnberg", "nuernberg"),
            List.of("vienna", "wien"),
            List.of("zurich", "zuerich"),
            List.of("geneva", "geneve", "genf"),
            List.of("prague", "praha"),
            List.of("warsaw", "warszawa"),
            List.of("krakow", "cracow"),
            List.of("lisbon", "lisboa"),
            List.of("copenhagen", "kobenhavn"),
            List.of("brussels", "bruxelles", "brussel"),
            List.of("the hague", "den haag"),
            List.of("athens", "athina"),
            List.of("bucharest", "bucuresti"),
            List.of("belgrade", "beograd"),
            List.of("seville", "sevilla"),
            List.of("hanoi", "ha noi"),
            List.of("ho chi minh city", "ho chi minh", "saigon", "hcmc"));

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private CityNames() {
    }

    /** Whether {@code locationText} names {@code city}; false when either is null or blank. */
    static boolean mentions(String locationText, String city) {
        if (locationText == null || locationText.isBlank() || city == null || city.isBlank()) {
            return false;
        }
        String location = fold(locationText);
        return names(city).stream().anyMatch(name -> Pattern
                .compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])")
                .matcher(location)
                .find());
    }

    /** The folded city name and its other names from {@link #SAME_CITY}. */
    static List<String> names(String city) {
        String folded = fold(city);
        return SAME_CITY.stream()
                .filter(group -> group.contains(folded))
                .findFirst()
                .orElse(List.of(folded));
    }

    static String fold(String text) {
        String decomposed = Normalizer.normalize(text.replace("ß", "ss"), Normalizer.Form.NFD);
        String plain = MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT).replace('đ', 'd');
        return WHITESPACE.matcher(plain).replaceAll(" ").strip();
    }
}
