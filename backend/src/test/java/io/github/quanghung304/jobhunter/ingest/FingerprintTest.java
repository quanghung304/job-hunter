package io.github.quanghung304.jobhunter.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.common.Hashing;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FingerprintTest {

    /** Each row: two (company, title) pairs that must be the same job. */
    @ParameterizedTest(name = "{0} / {1} == {2} / {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Acme, Inc.     | Senior Backend Engineer (Remote) - m/f/d | ACME Inc     | Senior Backend Engineer
            Acme           | Java Developer                           | Acme Ltd.    | Java Developer (m/w/d)
            Società Srl    | Sviluppatore Java                        | Societa SRL  | Sviluppatore Java
            Ferrari S.p.A. | Backend Developer                        | Ferrari SpA  | Backend Developer - Remote
            Nestlé S.A.    | Data Engineer                            | Nestle       | Data Engineer, Remote
            Zalando SE     | 'Software Engineer | Remote'             | zalando      | Software Engineer
            Acme GmbH & Co. KG | Werkstudent IT (w/m/d)               | ACME GmbH    | Werkstudent IT
            Wolt - English | Product Manager, Homepage                | Wolt English | Product Manager Homepage
            Tracium GmbH   | Full-Stack Engineer – AI                 | Tracium      | Full Stack Engineer AI
            Acme Corp      | [Remote] Platform Engineer               | Acme         | Platform Engineer (Remote)
            Acme LLC       | Backend/Platform Engineer                | ACME         | Backend - Platform Engineer
            Café Crème Ltd | Barista Developer                        | Cafe Creme   | BARISTA DEVELOPER
            """)
    void sameJob(String companyA, String titleA, String companyB, String titleB) {
        assertThat(Fingerprint.ofPosting(companyA, titleA)).isEqualTo(Fingerprint.ofPosting(companyB, titleB));
    }

    /** Each row: two pairs that must stay different jobs. */
    @ParameterizedTest(name = "{0} / {1} != {2} / {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Acme    | Senior Backend Engineer | Acme       | Backend Engineer
            Acme    | Java Developer          | Acme Media | Java Developer
            Acme    | C++ Developer           | Acme       | C Developer
            Acme    | Backend Engineer        | Apex       | Backend Engineer
            Acme    | Lead Engineer           | Acme       | Engineer
            Acme    | Engineer I              | Acme       | Engineer II
            Acme    | Data Engineer           | Acme       | Data Analyst
            Acme    | Remoteness Analyst      | Acme       | Analyst
            """)
    void differentJobs(String companyA, String titleA, String companyB, String titleB) {
        assertThat(Fingerprint.ofPosting(companyA, titleA)).isNotEqualTo(Fingerprint.ofPosting(companyB, titleB));
    }

    @Test
    void isSha256OfCompanyKeyPipeTitleKey() {
        assertThat(Fingerprint.of("acme", "java developer")).isEqualTo(Hashing.sha256Hex("acme|java developer"))
                .hasSize(64).matches("[0-9a-f]{64}");
        assertThat(Fingerprint.ofPosting("Acme, Inc.", "Java Developer (m/f/d)"))
                .isEqualTo(Fingerprint.of("acme", "java developer"));
    }

    @Test
    void repostAppendsTheUtcDateOfFirstSeen() {
        assertThat(Fingerprint.repost("abc", Instant.parse("2026-12-31T23:30:00Z"))).isEqualTo("abc#20261231");
    }
}
