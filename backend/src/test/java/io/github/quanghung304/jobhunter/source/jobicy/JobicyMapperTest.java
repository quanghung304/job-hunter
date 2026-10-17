package io.github.quanghung304.jobhunter.source.jobicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.RemoteHint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JobicyMapperTest {

    @ParameterizedTest(name = "{0}[{1}] {2}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            europe-engineering.json | 0 | 155587 | Monzo            | UK                | Full-Time | 170000 | 225000 | GBP | YEAR  | software engineering;director        | 2026-10-17T08:33:07Z
            europe-engineering.json | 1 | 155579 | Laminar Projects | Poland, Portugal  | Full-Time | -      | -      | -   | -     | software engineering                 | 2026-10-17T08:33:07Z
            europe-engineering.json | 2 | 155565 | GoFasti          | Anywhere          | Contract  | 2000   | 3000   | USD | MONTH | software engineering;senior          | 2026-10-17T06:55:52Z
            europe-engineering.json | 3 | 153452 | Supabase         | Anywhere          | Full-Time | -      | -      | -   | -     | software engineering                 | 2026-10-17T04:15:08Z
            europe-engineering.json | 4 | 155439 | Cision           | Hungary           | Full-Time | -      | -      | -   | -     | software engineering                 | 2026-10-16T13:31:01Z
            tag-java.json           | 2 | 155488 | Wrapbook         | Canada, USA       | Full-Time | 196000 | 267100 | USD | YEAR  | software engineering;senior          | 2026-10-16T16:46:28Z
            tag-java.json           | 4 | 155437 | Cision           | Bulgaria, Hungary | Full-Time | -      | -      | -   | -     | cybersecurity;entry-level;junior     | 2026-10-16T13:31:01Z
            """)
    void mapsEachFixtureItem(String file, int index, String id, String company, String location, String employment,
            BigDecimal min, BigDecimal max, String currency, SalaryPeriod period, String tags, Instant postedAt) {
        JsonNode raw = Fixtures.json("jobicy/" + file).get("jobs").get(index);

        NormalizedPosting posting = JobicyMapper.toPosting(dto(raw), raw);

        assertThat(posting.sourceCode()).isEqualTo("jobicy");
        assertThat(posting.externalId()).isEqualTo(id);
        assertThat(posting.url()).isEqualTo(raw.get("url").asText()).startsWith("https://jobicy.com/jobs/" + id);
        assertThat(posting.applyUrl()).isEqualTo(posting.url());
        assertThat(posting.title()).isEqualTo(raw.get("jobTitle").asText());
        assertThat(posting.companyName()).isEqualTo(company);
        assertThat(posting.locationText()).isEqualTo(location);
        assertThat(posting.remoteHint()).isEqualTo(RemoteHint.REMOTE);
        assertThat(posting.employmentHint()).isEqualTo(employment);
        if (min == null) {
            assertThat(posting.salaryMin()).isNull();
            assertThat(posting.salaryMax()).isNull();
        } else {
            assertThat(posting.salaryMin()).isEqualByComparingTo(min);
            assertThat(posting.salaryMax()).isEqualByComparingTo(max);
        }
        assertThat(posting.salaryCurrency()).isEqualTo(currency);
        assertThat(posting.salaryPeriod()).isEqualTo(period);
        assertThat(posting.tags()).isEqualTo(Arrays.asList(tags.split(";")));
        assertThat(posting.postedAt()).isEqualTo(postedAt);
        assertThat(posting.descriptionText()).isNotBlank().doesNotContain("<p>", "<li>", "</", "&amp;", "&lt;");
        assertThat(posting.rawPayload()).isSameAs(raw);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            2026-10-17T08:33:07+00:00 | 2026-10-17T08:33:07Z
            2026-10-17T10:33:07+02:00 | 2026-10-17T08:33:07Z
            2026-10-17T08:33:07Z      | 2026-10-17T08:33:07Z
            2026-10-10 12:00:00       | 2026-10-10T12:00:00Z
            2026-10-10T12:00:00       | 2026-10-10T12:00:00Z
            ' 2026-10-10 12:00:00 '   | 2026-10-10T12:00:00Z
            """)
    void parsesPubDateWithAndWithoutZone(String value, Instant expected) {
        assertThat(JobicyMapper.parsePubDate(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void blankPubDateIsNull(String value) {
        assertThat(JobicyMapper.parsePubDate(value)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"17/10/2026", "yesterday", "2026-13-01 00:00:00"})
    void unreadablePubDateIsRejected(String value) {
        assertThatThrownBy(() -> JobicyMapper.parsePubDate(value))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unreadable pubDate");
    }

    @Test
    void unsupportedSalaryPeriodIsKeptAsText() {
        var raw = (ObjectNode) Fixtures.json("jobicy/europe-engineering.json").get("jobs").get(0).deepCopy();
        raw.put("salaryPeriod", "weekly");

        NormalizedPosting posting = JobicyMapper.toPosting(dto(raw), raw);

        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.salaryPeriod()).isNull();
        assertThat(posting.descriptionText()).endsWith("\n\nSalary: 170000 - 225000 GBP (weekly)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "url"})
    void jobWithoutIdentityIsRejected(String field) {
        var raw = (ObjectNode) Fixtures.json("jobicy/tag-java.json").get("jobs").get(1).deepCopy();
        raw.remove(field);

        assertThatThrownBy(() -> JobicyMapper.toPosting(dto(raw), raw))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("missing " + field);
    }

    private static JobicyJobDto dto(JsonNode raw) {
        try {
            return Fixtures.JSON.treeToValue(raw, JobicyJobDto.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
