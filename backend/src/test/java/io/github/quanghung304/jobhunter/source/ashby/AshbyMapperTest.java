package io.github.quanghung304.jobhunter.source.ashby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.common.model.SalaryPeriod;
import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AshbyMapperTest {

    private static final CompanyBoard LINEAR = new CompanyBoard(1, "Linear", AtsType.ASHBY, "linear");
    private static final CompanyBoard RAMP = new CompanyBoard(2, "Ramp", AtsType.ASHBY, "ramp");

    @ParameterizedTest(name = "{0}[{1}] {3}")
    @CsvSource(delimiterString = " § ", nullValues = "-", textBlock = """
            linear § 0 § d3bc1ced-3ce4-4086-a050-555055dbb1ff § Senior / Staff Fullstack Engineer § Europe § REMOTE § product;engineering § 2021-04-27T20:13:45.158Z § -
            linear § 1 § d37b3d76-3080-47f9-8a19-60505573112c § Solutions Engineer, Europe § London § HYBRID § gtm;sales § 2026-04-08T14:58:35.814Z § -
            linear § 2 § f16dd5e9-ed10-4b8e-b5d7-1b8b5e3f002f § Creative Producer § North America, Europe § REMOTE § gtm;creative studio § 2026-10-21T23:20:54.426Z § -
            ramp   § 0 § 34413f8d-26bf-4bbc-8ade-eb309a0e2245 § Security Engineer, Cloud § New York, NY (HQ), Remote (Canada), Remote (US), Miami, FL § HYBRID § engineering;backend § 2026-04-07T17:12:35.753Z § 211400
            ramp   § 1 § f564dcf9-9390-4a3f-896f-8047a5086040 § Mobile Engineer, Android § New York, NY (HQ), Remote (Canada), San Francisco, CA, Remote (US) § HYBRID § engineering;mobile § 2025-07-31T21:58:34.674Z § 189000
            ramp   § 2 § 1515fe6d-1d8e-475b-a5ee-cefe43e78cb7 § Account Executive § London, UK § ONSITE § sales;ae - mid-market § 2026-06-08T19:17:53.481Z § -
            ramp   § 3 § e0b64893-0459-4d84-ac4f-7b50966ef024 § Product Manager | Growth § New York, NY (HQ), San Francisco, CA § ONSITE § product;product management § 2026-10-06T16:01:26.998Z § 230000
            """)
    void mapsEachFixtureJob(String file, int index, String id, String title, String location, RemoteHint remoteHint,
            String tags, Instant publishedAt, BigDecimal salaryMin) {
        JsonNode raw = Fixtures.json("ashby/" + file + ".json").get("jobs").get(index);
        CompanyBoard board = file.equals("linear") ? LINEAR : RAMP;

        NormalizedPosting posting = map(board, raw).orElseThrow();

        assertThat(posting.sourceCode()).isEqualTo("ashby");
        assertThat(posting.externalId()).isEqualTo(file + ":" + id);
        assertThat(posting.url()).isEqualTo("https://jobs.ashbyhq.com/" + file + "/" + id);
        assertThat(posting.applyUrl()).isEqualTo(posting.url() + "/application");
        assertThat(posting.title()).isEqualTo(title);
        assertThat(posting.companyName()).isEqualTo(board.name());
        assertThat(posting.locationText()).isEqualTo(location);
        assertThat(posting.remoteHint()).isEqualTo(remoteHint);
        assertThat(posting.employmentHint()).isEqualTo("FullTime");
        assertThat(posting.tags()).isEqualTo(Arrays.asList(tags.split(";")));
        assertThat(posting.postedAt()).isEqualTo(publishedAt);
        assertThat(posting.descriptionText()).isNotBlank().doesNotContain("<p", "</");
        if (salaryMin == null) {
            assertThat(posting.salaryMin()).isNull();
            assertThat(posting.salaryPeriod()).isNull();
        } else {
            assertThat(posting.salaryMin()).isEqualByComparingTo(salaryMin);
            assertThat(posting.salaryMax()).isNotNull();
            assertThat(posting.salaryCurrency()).isEqualTo("USD");
            assertThat(posting.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
        }
        assertThat(posting.rawPayload()).isSameAs(raw);
    }

    @Test
    void unlistedJobsAreSkipped() {
        ObjectNode raw = Fixtures.json("ashby/linear.json").get("jobs").get(0).deepCopy();
        raw.put("isListed", false);

        assertThat(map(LINEAR, raw)).isEmpty();
    }

    @Test
    void compensationWithoutASingleSalaryIsAppendedToTheDescription() {
        ObjectNode raw = Fixtures.json("ashby/ramp.json").get("jobs").get(0).deepCopy();
        ObjectNode salary = raw.withObject("/compensation").withArray("summaryComponents").addObject();
        salary.put("compensationType", "Salary").put("interval", "1 YEAR").put("currencyCode", "EUR")
                .put("minValue", 150000).put("maxValue", 200000);

        NormalizedPosting posting = map(RAMP, raw).orElseThrow();

        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.descriptionText()).endsWith("\n\nCompensation: "
                + raw.get("compensation").get("compensationTierSummary").asText());
    }

    @Test
    void withoutWorkplaceTypeIsRemoteFallsBack() {
        ObjectNode raw = Fixtures.json("ashby/linear.json").get("jobs").get(0).deepCopy();
        raw.putNull("workplaceType");
        assertThat(map(LINEAR, raw).orElseThrow().remoteHint()).isEqualTo(RemoteHint.REMOTE);

        raw.putNull("isRemote");
        assertThat(map(LINEAR, raw).orElseThrow().remoteHint()).isEqualTo(RemoteHint.UNKNOWN);
    }

    @Test
    void htmlIsUsedWithoutPlainDescription() {
        ObjectNode raw = Fixtures.json("ashby/linear.json").get("jobs").get(0).deepCopy();
        raw.putNull("descriptionPlain");

        assertThat(map(LINEAR, raw).orElseThrow().descriptionText()).startsWith("At Linear, we're building")
                .doesNotContain("<p");
    }

    @Test
    void missingIdOrUrlOrBadDateIsRejected() {
        ObjectNode noUrl = Fixtures.json("ashby/linear.json").get("jobs").get(0).deepCopy();
        noUrl.remove("jobUrl");
        ObjectNode badDate = Fixtures.json("ashby/linear.json").get("jobs").get(0).deepCopy();
        badDate.put("publishedAt", "last week");

        assertThatThrownBy(() -> map(LINEAR, noUrl)).hasMessage("missing jobUrl");
        assertThatThrownBy(() -> map(LINEAR, badDate)).hasMessage("unreadable publishedAt 'last week'");
    }

    private static Optional<NormalizedPosting> map(CompanyBoard board, JsonNode raw) {
        try {
            return AshbyMapper.toPosting(board, Fixtures.JSON.treeToValue(raw, AshbyJobDto.class), raw);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
