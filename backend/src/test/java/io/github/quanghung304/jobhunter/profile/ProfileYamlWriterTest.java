package io.github.quanghung304.jobhunter.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProfileYamlWriterTest {

    private static Profile read(String yaml) {
        return ProfileParser.fromYaml(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /** Profiles hold compiled regexes (no equals), so they are compared through their canonical YAML. */
    private static void assertSameProfile(Profile actual, Profile expected) {
        assertThat(ProfileYamlWriter.write(actual, null)).isEqualTo(ProfileYamlWriter.write(expected, null));
    }

    @Test
    void roundTripOfTheExampleKeepsEveryValue() throws IOException {
        Profile example = ProfileFixtures.example();
        JsonNode tracks = ProfileParser.YAML.readTree(Files.readAllBytes(ProfileFixtures.EXAMPLE)).get("tracks");

        String yaml = ProfileYamlWriter.write(example, tracks);
        Profile reread = read(yaml);

        assertSameProfile(reread, example);
        assertThat(reread.candidate().summary()).isEqualTo(example.candidate().summary());
        assertThat(reread.candidate().yearsExperience()).isEqualByComparingTo(example.candidate().yearsExperience());
        assertThat(reread.preferences().minSalary()).isEqualTo(example.preferences().minSalary());
        assertThat(reread.prefilter().titleInclude()).extracting(Pattern::pattern)
                .isEqualTo(example.prefilter().titleInclude().stream().map(Pattern::pattern).toList());
        assertThat(reread.tracks()).isEqualTo(example.tracks());
        assertThat(ProfileFixtures.load(ProfileFixtures.EXAMPLE).current().id()).isEqualTo(reread.id());
    }

    @Test
    void writingTwiceGivesTheSameBytes() {
        Profile example = ProfileFixtures.example();
        String once = ProfileYamlWriter.write(example, null);

        assertThat(ProfileYamlWriter.write(read(once), null)).isEqualTo(once);
    }

    @Test
    void keysFollowTheConfigSpecOrder() {
        String yaml = ProfileYamlWriter.write(ProfileFixtures.example(), null);

        assertThat(yaml).containsSubsequence("id:", "summaryLanguage:", "candidate:", "headline:", "yearsExperience:",
                "residenceCountry:", "timezone:", "languages:", "summary:", "skills:", "core:", "preferences:",
                "employmentTypes:", "acceptRegions:", "timezone:", "maxOffsetDiffHours:", "seniority:", "prefilter:",
                "maxAgeDays:", "titleInclude:", "minSkillHits:", "notify:", "digestMinScore:");
    }

    @Test
    void absentOptionalKeysAreLeftOutAndDefaultsWritten() {
        String yaml = ProfileYamlWriter.write(ProfileFixtures.resource("minimal.yaml"), null);

        assertThat(yaml).doesNotContain("null", "city:", "maxHoursPerWeek:", "minSalary:", "secondary:",
                "learning:", "domains:", "excludeCompanies:", "titleExclude:", "tracks:");
        assertThat(yaml).contains("summaryLanguage: en", "maxOffsetDiffHours: 6", "maxAgeDays: 21",
                "minSkillHits: 2", "digestMinScore: 65");
        assertSameProfile(read(yaml), ProfileFixtures.resource("minimal.yaml"));
    }

    @Test
    void rawTracksKeepTheirTrackSpecificKeys() throws IOException {
        JsonNode tracks = ProfileParser.YAML.readTree(Files.readAllBytes(ProfileFixtures.EXAMPLE)).get("tracks");

        String yaml = ProfileYamlWriter.write(ProfileFixtures.example(), tracks);

        JsonNode written = ProfileParser.YAML.readTree(yaml).get("tracks");
        assertThat(written).isEqualTo(tracks);
        assertThat(written.get(1).get("subjects")).isNotNull();
    }

    @Test
    void yamlLookalikesMultiLineTextAndRegexesSurvive() {
        String yaml = """
                id: norway
                candidate:
                  headline: "yes: a headline with a colon"
                  yearsExperience: 7.5
                  residenceCountry: "NO"
                  timezone: Europe/Oslo
                  languages:
                    - { code: "no", level: NATIVE }
                    - { code: "on", level: B2 }
                  summary: |
                    First line.
                    Second line with # hash and 'quotes'.
                  skills:
                    core: ["C#", "*nix", "true"]
                preferences:
                  employmentTypes: [CONTRACT]
                  acceptRegions: ["NO", EU]
                  seniority: [SENIOR]
                prefilter:
                  titleInclude: ['\\b(java|kotlin)\\b', '^senior\\s+.*$']
                  titleExclude: ['[Ii]ntern']
                """;
        Profile profile = read(yaml);

        String written = ProfileYamlWriter.write(profile, null);
        Profile reread = read(written);

        assertThat(reread.candidate().residenceCountry()).isEqualTo("NO");
        assertThat(reread.candidate().headline()).isEqualTo("yes: a headline with a colon");
        assertThat(reread.candidate().languages()).extracting(Profile.Language::code).containsExactly("no", "on");
        assertThat(reread.candidate().summary()).isEqualTo("First line.\nSecond line with # hash and 'quotes'.\n");
        assertThat(reread.candidate().skills().core()).containsExactly("C#", "*nix", "true");
        assertThat(reread.preferences().acceptRegions()).containsExactly("NO", "EU");
        assertThat(reread.prefilter().titleInclude()).extracting(Pattern::pattern)
                .containsExactly("\\b(java|kotlin)\\b", "^senior\\s+.*$");
        assertThat(reread.prefilter().titleExclude()).extracting(Pattern::pattern).containsExactly("[Ii]ntern");
        assertThat(written).contains("summary: |");
        assertSameProfile(reread, profile);
        assertThat(List.of(written.split("\n")).get(0)).startsWith("#");
    }
}
