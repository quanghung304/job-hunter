package io.github.quanghung304.jobhunter.source.arbeitnow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.RemoteHint;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArbeitnowMapperTest {

    private static final JsonNode ITEMS = Fixtures.json("arbeitnow/page1.json").get("data");

    @ParameterizedTest(name = "item {0}: {1}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            0 | remote-interim-senior-bi-analyst-all-genders-germany-169194 | Shiftmove             | Interim Senior BI Analyst (All Genders) | Germany                         | REMOTE | Contract                                                                 | computer software;remote      | 1792203608
            1 | qc-inspector-germany-326632                                 | Kirbygroupengineering | 'QC Inspector | Germany'                | -                               | ONSITE | -                                                                        | quality control k220          | 1792194924
            2 | full-stack-engineer-ai-authoring-platform-hamburg-483900    | Tracium GmbH          | -                                       | Hamburg                         | REMOTE | Freelancer / indepedent contractor, freelance, professional / experienced | remote;software development   | 1792191634
            3 | remote-head-of-urology-232681                               | Engagedmd             | Head of Urology                         | Remote                          | REMOTE | Full Time                                                                | market expansion              | 1792183223
            4 | product-manager-homepage-london-355976                      | Wolt - English        | Product Manager, Homepage               | London, England, United Kingdom | ONSITE | -                                                                        | -                             | 1792223128
            5 | contract-software-engineer-dom-core-firefox-gecko-402762    | mozilla               | Contract Software Engineer - DOM Core Firefox/Gecko | -                   | ONSITE | -                                                                        | firefox                       | 1792173331
            """)
    void mapsEachFixtureItem(int index, String externalId, String company, String title, String location,
            RemoteHint remoteHint, String employmentHint, String tags, long createdAt) {
        JsonNode raw = ITEMS.get(index);

        NormalizedPosting posting = ArbeitnowMapper.toPosting(dto(raw), raw);

        assertThat(posting.sourceCode()).isEqualTo("arbeitnow");
        assertThat(posting.externalId()).isEqualTo(externalId);
        assertThat(posting.url()).isEqualTo(raw.get("url").asText()).endsWith(externalId);
        assertThat(posting.applyUrl()).isEqualTo(posting.url());
        assertThat(posting.companyName()).isEqualTo(company);
        if (title != null) {
            assertThat(posting.title()).isEqualTo(title);
        } else {
            assertThat(posting.title()).startsWith("Full-Stack Engineer").endsWith("AI Authoring Platform");
        }
        assertThat(posting.locationText()).isEqualTo(location);
        assertThat(posting.remoteHint()).isEqualTo(remoteHint);
        assertThat(posting.employmentHint()).isEqualTo(employmentHint);
        assertThat(posting.tags()).isEqualTo(tags == null ? List.of() : Arrays.asList(tags.split(";")));
        assertThat(posting.postedAt()).isEqualTo(Instant.ofEpochSecond(createdAt));
        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.salaryPeriod()).isNull();
        assertThat(posting.rawPayload()).isSameAs(raw);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void descriptionIsPlainText(int index) {
        JsonNode raw = ITEMS.get(index);

        String text = ArbeitnowMapper.toPosting(dto(raw), raw).descriptionText();

        assertThat(text).isNotBlank().endsWith("on Arbeitnow")
                .doesNotContain("<p>", "<div", "<li>", "</", "&lt;", "&gt;", "&amp;", "&quot;", "&nbsp;");
    }

    @Test
    void doubleEscapedDescriptionIsDecoded() {
        JsonNode raw = ITEMS.get(1); // description arrives as "&lt;div class=&quot;content-intro&quot;&gt;..."

        String text = ArbeitnowMapper.toPosting(dto(raw), raw).descriptionText();

        assertThat(raw.get("description").asText()).startsWith("&lt;div");
        assertThat(text).startsWith("Permanent position available");
    }

    @Test
    void listItemsBecomeLines() {
        JsonNode raw = ITEMS.get(0);

        String text = ArbeitnowMapper.toPosting(dto(raw), raw).descriptionText();

        assertThat(raw.get("description").asText()).contains("<li>");
        assertThat(text).contains("\n");
    }

    @ParameterizedTest(name = "remote={0}, location={1} -> {2}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            true  | Berlin                   | REMOTE
            false | Remote                   | REMOTE
            false | remote                   | REMOTE
            false | Germany (Remote)         | REMOTE
            false | Remote, EU               | REMOTE
            false | Berlin / REMOTE possible | REMOTE
            false | Berlin                   | ONSITE
            false | -                        | ONSITE
            false | Remoteville               | ONSITE
            false | Fernarbeit               | ONSITE
            """)
    void remoteFlagOrRemoteLocationMeansRemote(boolean remote, String location, RemoteHint expected) {
        var raw = (ObjectNode) ITEMS.get(4).deepCopy();
        raw.put("remote", remote);
        if (location == null) {
            raw.remove("location");
        } else {
            raw.put("location", location);
        }

        assertThat(ArbeitnowMapper.toPosting(dto(raw), raw).remoteHint()).isEqualTo(expected);
        assertThat(ArbeitnowMapper.isRemote(dto(raw))).isEqualTo(expected == RemoteHint.REMOTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"slug", "url"})
    void itemWithoutIdentityIsRejected(String field) {
        var raw = (ObjectNode) ITEMS.get(3).deepCopy();
        raw.remove(field);

        assertThatThrownBy(() -> ArbeitnowMapper.toPosting(dto(raw), raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("missing " + field);
    }

    @Test
    void missingOptionalFieldsBecomeNullOrEmpty() {
        var raw = (ObjectNode) ITEMS.get(3).deepCopy();
        raw.remove(List.of("tags", "job_types", "location", "created_at", "remote", "description"));

        NormalizedPosting posting = ArbeitnowMapper.toPosting(dto(raw), raw);

        assertThat(posting.tags()).isEmpty();
        assertThat(posting.employmentHint()).isNull();
        assertThat(posting.locationText()).isNull();
        assertThat(posting.postedAt()).isNull();
        assertThat(posting.remoteHint()).isEqualTo(RemoteHint.ONSITE);
        assertThat(posting.descriptionText()).isEmpty();
    }

    private static ArbeitnowJobDto dto(JsonNode raw) {
        try {
            return Fixtures.JSON.treeToValue(raw, ArbeitnowJobDto.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
