package io.github.quanghung304.jobhunter.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegionMatcherTest {

    /** acceptRegions of config/profile.example.yaml. */
    private static final String EXAMPLE = "WORLDWIDE EU EEA EUROPE EMEA IT VN";

    @ParameterizedTest(name = "residence {0}, accept [{1}], job [{2}] -> {3}")
    @CsvSource(delimiter = '|', textBlock = """
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | EU            | OK
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | EMEA          | OK
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | WORLDWIDE     | OK
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | IT            | OK
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | VN            | OK
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | US            | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | US CA         | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | ''            | UNKNOWN
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | LATAM         | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | APAC          | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | NORTH_AMERICA | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | GB            | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | DE            | FAIL
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | US EU         | OK
            IT | WORLDWIDE EU EEA EUROPE EMEA IT VN | eu            | OK
            IT | WORLDWIDE EU GB                    | GB            | OK
            IT | ''                                 | EU            | OK
            IT | ''                                 | EEA           | OK
            IT | ''                                 | EUROPE        | OK
            IT | ''                                 | EMEA          | OK
            IT | ''                                 | WORLDWIDE     | OK
            IT | ''                                 | LATAM         | FAIL
            IT | LATAM                              | LATAM         | OK
            IT | ''                                 | APAC ASIA     | FAIL
            CH | ''                                 | EU            | FAIL
            CH | ''                                 | EEA           | FAIL
            CH | ''                                 | EUROPE        | OK
            CH | ''                                 | EMEA          | OK
            CH | EUROPE                             | EU            | FAIL
            CH | ''                                 | CH            | OK
            NO | ''                                 | EEA           | OK
            NO | ''                                 | EU            | FAIL
            GB | ''                                 | EU            | FAIL
            GB | ''                                 | EUROPE        | OK
            AE | ''                                 | EMEA          | OK
            NG | ''                                 | EMEA          | OK
            NG | ''                                 | EUROPE        | FAIL
            US | ''                                 | EMEA          | FAIL
            """)
    void appliesTheRegionRule(String residence, String accept, String allowed, RegionVerdict expected) {
        var matcher = new RegionMatcher(residence, tokens(accept));

        assertThat(matcher.isAccepted(tokens(allowed))).isEqualTo(expected);
    }

    @Test
    void noStatedRegionIsUnknown() {
        var matcher = new RegionMatcher("IT", tokens(EXAMPLE));

        assertThat(matcher.isAccepted(null)).isEqualTo(RegionVerdict.UNKNOWN);
        assertThat(matcher.isAccepted(List.of())).isEqualTo(RegionVerdict.UNKNOWN);
        assertThat(matcher.isAccepted(Arrays.asList(" ", null))).isEqualTo(RegionVerdict.UNKNOWN);
    }

    @Test
    void tokensAreTrimmedAndCaseInsensitive() {
        var matcher = new RegionMatcher("it", List.of(" vn "));

        assertThat(matcher.isAccepted(List.of(" Europe "))).isEqualTo(RegionVerdict.OK);
        assertThat(matcher.isAccepted(List.of("VN"))).isEqualTo(RegionVerdict.OK);
    }

    @Test
    void usesTheProfileResidenceAndAcceptRegions() {
        var matcher = RegionMatcher.of(ProfileFixtures.example());

        assertThat(matcher.isAccepted(List.of("EU"))).isEqualTo(RegionVerdict.OK);
        assertThat(matcher.isAccepted(List.of("US", "CA"))).isEqualTo(RegionVerdict.FAIL);
        assertThat(matcher.isAccepted(List.of())).isEqualTo(RegionVerdict.UNKNOWN);
    }

    @Test
    void membershipMapsAreCompleteAndNested() {
        assertThat(RegionMatcher.EU).hasSize(27).doesNotContain("GB", "CH", "NO");
        assertThat(RegionMatcher.EEA).hasSize(30).containsAll(RegionMatcher.EU).contains("IS", "LI", "NO");
        assertThat(RegionMatcher.EUROPE).containsAll(RegionMatcher.EEA).contains("CH", "GB", "UA");
        assertThat(RegionMatcher.EMEA).containsAll(RegionMatcher.EUROPE).contains("AE", "ZA");
        assertThat(RegionMatcher.AFRICA).hasSize(54);
    }

    private static List<String> tokens(String spaceSeparated) {
        return spaceSeparated.isBlank() ? List.of() : List.of(spaceSeparated.trim().split("\\s+"));
    }
}
