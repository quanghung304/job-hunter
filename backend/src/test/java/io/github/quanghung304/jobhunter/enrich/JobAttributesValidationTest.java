package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.common.model.LanguageLevel;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.llm.json.InvalidLlmOutputException;
import io.github.quanghung304.jobhunter.llm.json.JsonExtractor;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** JobAttributes as the router parses model answers (JsonExtractor + Bean Validation). */
class JobAttributesValidationTest {

    static final String VALID = """
            {"remoteType":"REMOTE_RESTRICTED","allowedRegions":["eu","IT"],"regionEvidence":"Remote within the EU",
             "timezoneRequirement":{"minUtcOffset":-1,"maxUtcOffset":3,"overlapHours":null},
             "employmentTypes":["CONTRACT","FREELANCE"],"hoursPerWeek":{"min":30,"max":40},"seniority":"SENIOR",
             "minYearsExperience":5,"requiredSkills":["Java","Spring Boot"],"niceToHaveSkills":["Kafka"],
             "requiredLanguages":[{"code":"EN","level":"C1"}],"workAuthorization":null,
             "salary":{"min":60,"max":75,"currency":"EUR","period":"HOUR"},
             "roleSummary":"Build payment APIs.","isAgencyOrReposting":false,"isScamSignal":false,
             "confidence":"HIGH","extraField":"ignored"}""";

    final JsonExtractor extractor = new JsonExtractor(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validAnswerIsMappedAndNormalized() {
        JobAttributes a = extractor.extract(VALID, JobAttributes.class);

        assertThat(a.remoteType()).isEqualTo(JobAttributes.RemoteType.REMOTE_RESTRICTED);
        assertThat(a.allowedRegions()).containsExactly("EU", "IT");
        assertThat(a.timezoneRequirement()).isEqualTo(new JobAttributes.TimezoneRequirement(-1, 3, null));
        assertThat(a.employmentTypes()).containsExactly(EmploymentType.CONTRACT, EmploymentType.FREELANCE);
        assertThat(a.requiredLanguages()).containsExactly(new JobAttributes.RequiredLanguage("en", LanguageLevel.C1));
        assertThat(a.salary()).isEqualTo(new JobAttributes.Salary(new BigDecimal("60"), new BigDecimal("75"), "EUR",
                SalaryPeriod.HOUR));
        assertThat(a.confidence()).isEqualTo(JobAttributes.Confidence.HIGH);
    }

    @Test
    void missingListsBecomeEmptyAndLongTextIsCut() {
        String answer = """
                {"remoteType":"UNKNOWN","roleSummary":"%s","regionEvidence":"%s","isAgencyOrReposting":true,
                 "isScamSignal":false,"confidence":"LOW"}""".formatted("x".repeat(400), "y".repeat(250));

        JobAttributes a = extractor.extract(answer, JobAttributes.class);

        assertThat(a.allowedRegions()).isEmpty();
        assertThat(a.employmentTypes()).isEmpty();
        assertThat(a.requiredSkills()).isEmpty();
        assertThat(a.requiredLanguages()).isEmpty();
        assertThat(a.roleSummary()).hasSize(JobAttributes.ROLE_SUMMARY_MAX).endsWith("…");
        assertThat(a.regionEvidence()).hasSize(JobAttributes.EVIDENCE_MAX);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', textBlock = """
            unknown remote type   | "remoteType":"REMOTE"                       | does not match the expected shape
            unknown region token  | "allowedRegions":["Europe/West"]            | allowedRegions
            unknown employment    | "employmentTypes":["GIG"]                   | does not match the expected shape
            bad language code     | "requiredLanguages":[{"code":"eng","level":"C1"}] | requiredLanguages
            bad language level    | "requiredLanguages":[{"code":"de","level":"FLUENT"}] | does not match the expected shape
            too many skills       | "requiredSkills":["a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p"] | requiredSkills
            offset out of range   | "timezoneRequirement":{"minUtcOffset":-20,"maxUtcOffset":3} | timezoneRequirement.minUtcOffset
            bad currency          | "salary":{"min":1,"currency":"euro","period":"YEAR"} | salary.currency
            blank summary         | "roleSummary":"  "                          | roleSummary
            missing confidence    | "confidence":null                           | confidence
            """)
    void invalidAnswersAreRejectedForTheCorrectiveRetry(String name, String field, String message) {
        String answer = base().replace(fieldPlaceholder(field), field);

        assertThatThrownBy(() -> extractor.extract(answer, JobAttributes.class))
                .isInstanceOf(InvalidLlmOutputException.class)
                .hasMessageContaining(message);
    }

    /** A valid answer whose field named in {@code field} is replaced by the row's value. */
    private static String base() {
        return """
                {"remoteType":"FULLY_REMOTE","allowedRegions":["EU"],"employmentTypes":["CONTRACT"],
                 "requiredSkills":["Java"],"requiredLanguages":[],"timezoneRequirement":null,"salary":null,
                 "roleSummary":"Work.","isAgencyOrReposting":false,"isScamSignal":false,"confidence":"HIGH"}""";
    }

    private static String fieldPlaceholder(String field) {
        String key = field.substring(0, field.indexOf(':'));
        String base = base();
        int start = base.indexOf(key);
        int end = start + key.length() + 1;
        int depth = 0;
        for (int i = end; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                if (depth == 0) {
                    return base.substring(start, i);
                }
                depth--;
                if (depth == 0) {
                    return base.substring(start, i + 1);
                }
            } else if (c == ',' && depth == 0) {
                return base.substring(start, i);
            }
        }
        throw new IllegalArgumentException(field);
    }
}
