package io.github.quanghung304.jobhunter.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CandidateBlockTest {

    @Test
    void rendersTheFullBlock() {
        String block = ProfileFixtures.resource("candidate-block.yaml").candidateBlock();

        assertThat(block).isEqualTo("""
                Headline: Backend Engineer (Java)
                Experience: 4.5 years
                Lives in: Turin, IT (timezone Europe/Rome)
                Languages: vi NATIVE, en C1
                Summary:
                - 2023–2026: Java developer at a bank.
                - Contact me at [redacted] or [redacted].
                Core skills: Java, Spring Boot
                Secondary skills: Kafka
                Learning: React
                Domains: banking
                Preferences:
                - Employment types: CONTRACT, PART_TIME
                - Seniority: MID, SENIOR
                - Max hours per week: 25
                - Can work under region restrictions: EU, IT
                - Max timezone difference: 6 hours
                - Minimum salary: 40000 EUR per YEAR""");
    }

    @Test
    void omitsEmptyOptionalLines() {
        String block = ProfileFixtures.resource("minimal.yaml").candidateBlock();

        assertThat(block).isEqualTo("""
                Headline: Developer
                Experience: 2 years
                Lives in: DE (timezone Europe/Berlin)
                Languages: en B2
                Summary:
                Builds web apps.
                Core skills: Kotlin
                Preferences:
                - Employment types: FULL_TIME
                - Seniority: MID
                - Can work under region restrictions: EU
                - Max timezone difference: 6 hours""");
    }

    @Test
    void exampleBlockHasNoContactDataOrInternalSettings() {
        var profile = ProfileFixtures.example();
        String block = profile.candidateBlock();

        assertThat(block)
                .startsWith("Headline: Backend Software Engineer")
                .contains("Core skills: Java, Spring Boot", "Experience: 4 years")
                .doesNotContain("@", "[redacted]", profile.id(), "titleInclude", "digestMinScore", "tutoring");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            mail me: a.b+jobs@mail.example.com      | mail me: [redacted]
            call +39 333 123 4567 today              | call [redacted] today
            tel 02/1234-5678                         | tel [redacted]
            2023-2026 at a bank                      | 2023-2026 at a bank
            ~40,000 users, team of 5                 | ~40,000 users, team of 5
            course since Sep 2026, 4.5 years         | course since Sep 2026, 4.5 years
            """)
    void redactsEmailsAndPhoneNumbersOnly(String text, String expected) {
        assertThat(CandidateBlock.redactContacts(text)).isEqualTo(expected);
    }
}
