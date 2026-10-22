package io.github.quanghung304.jobhunter.source.greenhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GreenhouseMapperTest {

    private static final CompanyBoard GITLAB = new CompanyBoard(1, "GitLab", AtsType.GREENHOUSE, "gitlab");
    private static final JsonNode JOBS = Fixtures.json("greenhouse/gitlab.json").get("jobs");

    @ParameterizedTest(name = "[{0}] {2}")
    @CsvSource(delimiterString = " § ", textBlock = """
            0 § 8790102002 § Commercial Account Executive - Mid-Market, East § Remote § REMOTE § amer - commercial § 2026-09-10T17:32:44Z
            1 § 8687926002 § Backend Engineer, Create: Source Code Experience (Ruby) § Remote, Canada § REMOTE § devops engineering § 2026-08-13T19:59:22Z
            2 § 8864819002 § Benefits Analyst § Bangalore, India § UNKNOWN § total rewards § 2026-10-09T15:09:56Z
            3 § 8532274002 § Business Development Representative § Remote, France; Remote, Ireland; Remote, Spain; Remote, United Kingdom § REMOTE § sales development § 2026-05-01T15:28:37Z
            4 § 8872457002 § Business Development Representative, German Speaking - UK/I § Remote Ireland; Remote, United Kingdom § REMOTE § sales development § 2026-10-07T17:32:58Z
            """)
    void mapsEachFixtureJob(int index, String id, String title, String location, RemoteHint remoteHint, String tag,
            Instant postedAt) {
        JsonNode raw = JOBS.get(index);

        NormalizedPosting posting = map(raw);

        assertThat(posting.sourceCode()).isEqualTo("greenhouse");
        assertThat(posting.externalId()).isEqualTo("gitlab:" + id);
        assertThat(posting.url()).isEqualTo("https://job-boards.greenhouse.io/gitlab/jobs/" + id);
        assertThat(posting.applyUrl()).isEqualTo(posting.url());
        assertThat(posting.title()).isEqualTo(title);
        assertThat(posting.companyName()).isEqualTo("GitLab");
        assertThat(posting.locationText()).isEqualTo(location);
        assertThat(posting.remoteHint()).isEqualTo(remoteHint);
        assertThat(posting.tags()).containsExactly(tag);
        assertThat(posting.postedAt()).isEqualTo(postedAt);
        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.descriptionText()).startsWith("GitLab is the intelligent orchestration platform")
                .doesNotContain("&lt;", "&gt;", "&quot;", "&amp;", "<p>", "<div", "</");
        assertThat(posting.rawPayload()).isSameAs(raw);
    }

    @Test
    void contentEntitiesAreUnescapedBeforeHtmlToText() {
        assertThat(GreenhouseMapper.description("&lt;p&gt;Tom &amp;amp; Jerry&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Java&lt;/li&gt;&lt;/ul&gt;"))
                .isEqualTo("Tom & Jerry\n\nJava");
    }

    @Test
    void updatedAtIsUsedWithoutFirstPublished() {
        ObjectNode raw = JOBS.get(0).deepCopy();
        raw.putNull("first_published");

        assertThat(map(raw).postedAt()).isEqualTo(Instant.parse("2026-10-15T19:24:46Z"));
    }

    @Test
    void missingIdOrUrlIsRejected() {
        ObjectNode noUrl = JOBS.get(0).deepCopy();
        noUrl.remove("absolute_url");
        ObjectNode noId = JOBS.get(0).deepCopy();
        noId.remove("id");

        assertThatThrownBy(() -> map(noUrl)).hasMessage("missing absolute_url");
        assertThatThrownBy(() -> map(noId)).hasMessage("missing id");
    }

    private static NormalizedPosting map(JsonNode raw) {
        try {
            return GreenhouseMapper.toPosting(GITLAB, Fixtures.JSON.treeToValue(raw, GreenhouseJobDto.class), raw);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError(e);
        }
    }
}
