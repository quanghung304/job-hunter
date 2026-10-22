package io.github.quanghung304.jobhunter.source.lever;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeverMapperTest {

    private static final CompanyBoard SPOTIFY = new CompanyBoard(1, "Spotify", AtsType.LEVER, "spotify");
    private static final JsonNode POSTINGS = Fixtures.json("lever/spotify.json");

    @ParameterizedTest(name = "[{0}] {2}")
    @CsvSource(delimiterString = " § ", nullValues = "-", textBlock = """
            0 § 4310f31c-90e1-4e8d-bdcf-dce39c145b8c § New York, NY § REMOTE § Permanent § operations and business support;audio books & gated content § 1783608935950
            1 § 223f9722-3579-4ff2-b123-e4cc7b0c1a64 § Mexico City § HYBRID § Permanent § markets and subscriber growth;marketing § 1791566108174
            2 § 785989a6-b219-43b1-92d9-750a67054f24 § Stockholm § ONSITE § Permanent § engineering;it and network engineering § 1791537508142
            3 § 03437e2a-2d5e-4593-9e97-11271014932e § London § REMOTE § - § data and analytics;platform § 1776160237237
            4 § 926149ab-6999-4058-b35d-41e302b70e05 § Stockholm, London § HYBRID § Permanent § engineering;platform § 1790854925650
            """)
    void mapsEachFixturePosting(int index, String id, String location, RemoteHint remoteHint, String commitment,
            String tags, long createdAt) {
        JsonNode raw = POSTINGS.get(index);

        NormalizedPosting posting = map(raw);

        assertThat(posting.sourceCode()).isEqualTo("lever");
        assertThat(posting.externalId()).isEqualTo("spotify:" + id);
        assertThat(posting.url()).isEqualTo("https://jobs.lever.co/spotify/" + id);
        assertThat(posting.applyUrl()).isEqualTo("https://jobs.lever.co/spotify/" + id + "/apply");
        assertThat(posting.title()).isEqualTo(raw.get("text").asText());
        assertThat(posting.companyName()).isEqualTo("Spotify");
        assertThat(posting.locationText()).isEqualTo(location);
        assertThat(posting.remoteHint()).isEqualTo(remoteHint);
        assertThat(posting.employmentHint()).isEqualTo(commitment);
        assertThat(posting.tags()).isEqualTo(Arrays.asList(tags.split(";")));
        assertThat(posting.postedAt()).isEqualTo(Instant.ofEpochMilli(createdAt));
        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.descriptionText()).startsWith(raw.get("descriptionPlain").asText().strip())
                .doesNotContain("<li>", "<div>", "</");
        assertThat(posting.rawPayload()).isSameAs(raw);
    }

    @Test
    void listsAreIncludedWithTheirHeadingsBetweenDescriptionAndAdditional() {
        JsonNode raw = POSTINGS.get(1);

        String text = map(raw).descriptionText();

        int whatYouDo = text.indexOf("What You'll Do\n");
        int whoYouAre = text.indexOf("Who You Are\n");
        int where = text.indexOf("Where You'll Be\n");
        int additional = text.indexOf(raw.get("additionalPlain").asText().strip().substring(0, 40));
        assertThat(whatYouDo).isPositive();
        assertThat(whoYouAre).isGreaterThan(whatYouDo);
        assertThat(where).isGreaterThan(whoYouAre);
        assertThat(additional).isGreaterThan(where);
        assertThat(text).contains("Bring creative concepts to life through strong visual direction");
    }

    @Test
    void structuredSalaryRangeIsMapped() {
        // salaryRange was not on any board checked (2026-10-22); shape from the Lever Postings API docs
        ObjectNode raw = POSTINGS.get(0).deepCopy();
        raw.putObject("salaryRange").put("min", 90000).put("max", 120000).put("currency", "EUR")
                .put("interval", "per-year-salary");

        NormalizedPosting posting = map(raw);

        assertThat(posting.salaryMin()).isEqualByComparingTo(new BigDecimal("90000"));
        assertThat(posting.salaryMax()).isEqualByComparingTo(new BigDecimal("120000"));
        assertThat(posting.salaryCurrency()).isEqualTo("EUR");
        assertThat(posting.salaryPeriod()).isEqualTo(SalaryPeriod.YEAR);
    }

    @Test
    void unknownSalaryIntervalLeavesSalaryEmpty() {
        ObjectNode raw = POSTINGS.get(0).deepCopy();
        raw.putObject("salaryRange").put("min", 1).put("max", 2).put("currency", "EUR").put("interval", "one-time");

        assertThat(map(raw).salaryPeriod()).isNull();
        assertThat(map(raw).salaryMin()).isNull();
    }

    @Test
    void missingIdOrHostedUrlIsRejected() {
        ObjectNode noUrl = POSTINGS.get(0).deepCopy();
        noUrl.remove("hostedUrl");
        ObjectNode noId = POSTINGS.get(0).deepCopy();
        noId.remove("id");

        assertThatThrownBy(() -> map(noUrl)).hasMessage("missing hostedUrl");
        assertThatThrownBy(() -> map(noId)).hasMessage("missing id");
    }

    private static NormalizedPosting map(JsonNode raw) {
        try {
            return LeverMapper.toPosting(SPOTIFY, Fixtures.JSON.treeToValue(raw, LeverPostingDto.class), raw);
        } catch (JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
