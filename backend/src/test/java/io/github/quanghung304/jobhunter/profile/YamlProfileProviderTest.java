package io.github.quanghung304.jobhunter.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.common.model.Seniority;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class YamlProfileProviderTest {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @TempDir
    Path tmp;

    @Test
    void loadsTheCommittedExample() {
        Profile profile = ProfileFixtures.example();

        assertThat(profile.id()).isEqualTo("hugh");
        assertThat(profile.summaryLanguage()).isEqualTo(SummaryLanguage.VI);
        var candidate = profile.candidate();
        assertThat(candidate.yearsExperience()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(candidate.residenceCountry()).isEqualTo("IT");
        assertThat(candidate.timezone()).isEqualTo(ZoneId.of("Europe/Rome"));
        assertThat(candidate.languages()).extracting(Profile.Language::level)
                .containsExactly(LanguageLevel.NATIVE, LanguageLevel.C1);
        assertThat(candidate.skills().core()).contains("Java", "Spring Boot");
        var preferences = profile.preferences();
        assertThat(preferences.employmentTypes())
                .containsExactly(EmploymentType.PART_TIME, EmploymentType.CONTRACT, EmploymentType.FREELANCE);
        assertThat(preferences.seniority()).containsExactly(Seniority.JUNIOR, Seniority.MID, Seniority.SENIOR);
        assertThat(preferences.minSalary().period()).isEqualTo(SalaryPeriod.HOUR);
        assertThat(profile.tracks()).extracting(Profile.Track::name).containsExactly("software", "tutoring");
    }

    @Test
    void emptyOptionalValuesGetTheirDefaults() {
        var preferences = ProfileFixtures.example().preferences();

        // The example leaves these keys empty.
        assertThat(preferences.maxHoursPerWeek()).isNull();
        assertThat(preferences.timezone().maxOffsetDiffHours()).isEqualTo(6);
    }

    @Test
    void missingOptionalSectionsGetTheirDefaults() {
        Profile profile = ProfileFixtures.resource("minimal.yaml");

        assertThat(profile.summaryLanguage()).isEqualTo(SummaryLanguage.EN);
        assertThat(profile.candidate().city()).isNull();
        assertThat(profile.candidate().domains()).isEmpty();
        assertThat(profile.candidate().skills().secondary()).isEmpty();
        assertThat(profile.preferences().timezone().maxOffsetDiffHours()).isEqualTo(6);
        assertThat(profile.preferences().minSalary()).isNull();
        assertThat(profile.preferences().excludeCompanies()).isEmpty();
        assertThat(profile.prefilter().maxAgeDays()).isEqualTo(21);
        assertThat(profile.prefilter().minSkillHits()).isEqualTo(2);
        assertThat(profile.prefilter().titleExclude()).isEmpty();
        assertThat(profile.notifySettings().digestMinScore()).isEqualTo(65);
        assertThat(profile.tracks()).isEmpty();
    }

    @Test
    void titleRegexesAreCaseInsensitive() {
        var prefilter = ProfileFixtures.example().prefilter();

        assertThat(prefilter.titleInclude()).anyMatch(p -> p.matcher("Senior JAVA Developer").find());
        assertThat(prefilter.titleExclude()).anyMatch(p -> p.matcher("Engineering MANAGER").find());
    }

    @Test
    void listsAreImmutable() {
        var core = ProfileFixtures.example().candidate().skills().core();

        assertThatThrownBy(() -> core.add("COBOL")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void hashIsSha256OfTheFileBytes() throws Exception {
        var expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ProfileFixtures.EXAMPLE)));

        assertThat(ProfileFixtures.load(ProfileFixtures.EXAMPLE).profileHash()).isEqualTo(expected).hasSize(64);
    }

    @Test
    void removedAlertMinScoreIsIgnored() throws Exception {
        Path old = writeExample(root -> root.with("notify").put("alertMinScore", 90));

        assertThat(ProfileFixtures.load(old).current().notifySettings().digestMinScore()).isEqualTo(65);
    }

    @Test
    void hashChangesWhenTheFileChanges() throws Exception {
        String original = ProfileFixtures.load(ProfileFixtures.EXAMPLE).profileHash();
        Path edited = writeExample(root -> root.with("notify").put("digestMinScore", 70));

        assertThat(ProfileFixtures.load(edited).profileHash()).isNotEqualTo(original);
    }

    @Test
    void unsupportedTracksAreIgnoredWithAWarning(CapturedOutput output) {
        ProfileFixtures.example();

        assertThat(output).contains("WARN").contains("track 'tutoring' is not implemented");
        assertThat(output).doesNotContain("track 'software'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "id", "candidate", "candidate.headline", "candidate.yearsExperience",
            "candidate.residenceCountry", "candidate.timezone", "candidate.languages",
            "candidate.summary", "candidate.skills", "candidate.skills.core", "preferences",
            "preferences.employmentTypes", "preferences.acceptRegions", "preferences.seniority",
            "prefilter", "prefilter.titleInclude"
    })
    void missingRequiredFieldIsReportedByPath(String field) throws Exception {
        Path file = writeExample(root -> {
            int dot = field.lastIndexOf('.');
            var parent = dot < 0 ? root : (ObjectNode) root.at("/" + field.substring(0, dot).replace('.', '/'));
            parent.remove(field.substring(dot + 1));
        });

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOfSatisfying(InvalidProfileException.class, e -> assertThat(e.problems())
                        .anyMatch(problem -> problem.startsWith(field + ": ")))
                .hasMessageContaining(file.toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            candidate.headline        | '   '         | candidate.headline: must not be blank
            id                        | Hugh Cao      | id: must match
            candidate.residenceCountry| Italy         | candidate.residenceCountry: must be an ISO-3166 alpha-2 code
            candidate.yearsExperience | -1            | candidate.yearsExperience: must be greater than or equal to 0
            preferences.maxHoursPerWeek | 0           | preferences.maxHoursPerWeek: must be greater than 0
            notify.digestMinScore     | 101           | notify.digestMinScore: must be less than or equal to 100
            """)
    void invalidValueIsReportedByPath(String field, String value, String expected) throws Exception {
        Path file = writeExample(root -> setValue(root, field, value));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOfSatisfying(InvalidProfileException.class, e -> assertThat(e.problems())
                        .anyMatch(problem -> problem.startsWith(expected)));
    }

    @Test
    void invalidRegexIsReportedByPath() throws Exception {
        Path file = writeExample(root -> ((ArrayNode) root.at("/prefilter/titleInclude")).set(1, "(unclosed"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("prefilter.titleInclude[1]")
                .hasMessageContaining("invalid regex");
    }

    @Test
    void invalidEnumIsReportedByPath() throws Exception {
        Path file = writeExample(root -> ((ArrayNode) root.at("/preferences/seniority")).add("GURU"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("preferences.seniority[3]")
                .hasMessageContaining("GURU");
    }

    @Test
    void invalidLanguageLevelIsReportedByPath() throws Exception {
        Path file = writeExample(root -> ((ObjectNode) root.at("/candidate/languages/1")).put("level", "FLUENT"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("candidate.languages[1].level");
    }

    @Test
    void unsupportedSummaryLanguageIsRejected() throws Exception {
        Path file = writeExample(root -> root.put("summaryLanguage", "fr"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("summaryLanguage");
    }

    @Test
    void invalidTimezoneIsRejected() throws Exception {
        Path file = writeExample(root -> root.with("candidate").put("timezone", "Mars/Olympus"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("candidate.timezone");
    }

    @Test
    void unknownRegionTokenIsRejected() throws Exception {
        Path file = writeExample(root -> ((ArrayNode) root.at("/preferences/acceptRegions")).add("EUROP"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("preferences.acceptRegions[7]: must be a region token");
    }

    @Test
    void misspelledKeyIsRejected() throws Exception {
        Path file = writeExample(root -> root.with("candidate").put("headlin", "typo"));

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("candidate.headlin");
    }

    @Test
    void allViolationsAreReportedAtOnce() throws Exception {
        Path file = writeExample(root -> {
            root.with("candidate").remove("headline");
            root.with("preferences").remove("seniority");
        });

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOfSatisfying(InvalidProfileException.class, e -> assertThat(e.problems()).hasSize(2));
    }

    @Test
    void missingFileExplainsHowToCreateIt() {
        assertThatThrownBy(() -> ProfileFixtures.load(tmp.resolve("profile.yaml")))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("file not found")
                .hasMessageContaining("profile.example.yaml");
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        Path file = Files.writeString(tmp.resolve("profile.yaml"), "# nothing here\n");

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("file is empty");
    }

    @Test
    void yamlSyntaxErrorNamesTheLine() throws Exception {
        Path file = Files.writeString(tmp.resolve("profile.yaml"), "id: x\ncandidate:\n  headline: \"unterminated\n");

        assertThatThrownBy(() -> ProfileFixtures.load(file))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessageContaining("YAML syntax error at line");
    }

    private Path writeExample(Consumer<ObjectNode> change) throws Exception {
        var root = (ObjectNode) YAML.readTree(ProfileFixtures.EXAMPLE.toFile());
        change.accept(root);
        return Files.writeString(tmp.resolve("profile.yaml"), YAML.writeValueAsString(root));
    }

    private static void setValue(ObjectNode root, String field, String value) {
        int dot = field.lastIndexOf('.');
        ObjectNode parent = dot < 0 ? root : root.withObject("/" + field.substring(0, dot).replace('.', '/'));
        String name = field.substring(dot + 1);
        if (value.matches("-?\\d+")) {
            parent.put(name, Integer.parseInt(value));
        } else {
            parent.put(name, value);
        }
    }
}
