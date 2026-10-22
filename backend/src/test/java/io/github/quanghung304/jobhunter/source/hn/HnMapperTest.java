package io.github.quanghung304.jobhunter.source.hn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HnMapperTest {

    private static final JsonNode CHILDREN = Fixtures.json("hn-whoishiring/item-october-2026.json").get("children");

    @ParameterizedTest(name = "[{0}] {2}")
    @CsvSource(delimiterString = " § ", nullValues = "-", textBlock = """
            0  § 49922609 § Sidekick Labs § Senior SWE, R&D Engineer, Intelligence Engineer § REMOTE § Full-time § https://www.linkedin.com/in/three-two-one § 2026-10-01T15:03:36Z
            1  § 49922584 § PrairieLearn § Full-Stack Software Engineer § REMOTE § - § https://www.prairielearn.com § 2026-10-01T15:02:37Z
            2  § 49925526 § Octane § Senior Software Engineer, Decisioning § REMOTE § - § https://octane.co/o/who-we-are/careers/jobs-open/?gh_jid=7997608003&gh_src=f2f474523us § 2026-10-01T18:47:24Z
            3  § 49922593 § Shepherd (Series B) § See description § ONSITE § - § https://shepherdinsurance.com/careers § 2026-10-01T15:02:51Z
            4  § 49922616 § PAGNOS § Cloud Engineer, AWS Serverless (TypeScript/Node.js) § REMOTE § Full-time § https://germantechjobs.de/jobs/PAGNOS-GmbH-Cloud-AI-Engineer--AWS-Serverless-mwd § 2026-10-01T15:04:11Z
            6  § 49922881 § Smarkets § See description § HYBRID § Full Time § https://www.smarkets.com § 2026-10-01T15:20:57Z
            7  § 49924674 § DuckDuckGo § See description § REMOTE § - § https://jobs.ashbyhq.com/duck-duck-go/a447e823-fd8f-438f-909 § 2026-10-01T17:37:37Z
            8  § 49922929 § Fastly § Software Engineers (Senior, Staff, Principal) § ONSITE § Full-time § https://www.fastly.com/about/careers § 2026-10-01T15:24:06Z
            9  § 49922899 § Mainmatter § Lead Rust Engineering Consultant § REMOTE § Full-time § https://mainmatter.com/rust-consulting/ § 2026-10-01T15:22:08Z
            10 § 49928066 § All Gravy § Senior Software Engineer (Full-stack TypeScript) § REMOTE § Full-time § https://nordictechjobs.com/jobs/3MlSv0uALS § 2026-10-01T23:01:24Z
            11 § 49930656 § Vistulo § Senior Java, C#, React & Calypso engineers, Calypso BA/PO § REMOTE § B2B contract, pay ranges below § https://ats.vistulo.com/roles/253cbd69-1019-4547-93b6-6ef8dbc9e771/java-trading-systems-engineer-2026-06/hn/ § 2026-10-02T07:09:22Z
            """)
    void mapsEachFixtureComment(int index, String id, String company, String title, RemoteHint remoteHint,
            String employment, String applyUrl, Instant postedAt) {
        JsonNode raw = CHILDREN.get(index);
        HnCommentDto comment = dto(raw);

        assertThat(HnMapper.isPost(comment)).isTrue();
        NormalizedPosting posting = HnMapper.toPosting(comment, raw);

        assertThat(posting.sourceCode()).isEqualTo("hn-whoishiring");
        assertThat(posting.externalId()).isEqualTo(id);
        assertThat(posting.url()).isEqualTo("https://news.ycombinator.com/item?id=" + id);
        assertThat(posting.companyName()).isEqualTo(company);
        assertThat(posting.title()).isEqualTo(title);
        assertThat(posting.remoteHint()).isEqualTo(remoteHint);
        assertThat(posting.employmentHint()).isEqualTo(employment);
        assertThat(posting.applyUrl()).isEqualTo(applyUrl);
        assertThat(posting.postedAt()).isEqualTo(postedAt);
        assertThat(posting.descriptionText()).isNotBlank().startsWith(company.split(" ")[0])
                .doesNotContain("<p>", "<a ", "&#x2F;", "&amp;", "&#x27;");
        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.salaryPeriod()).isNull();
        assertThat(posting.tags()).isEmpty();
    }

    @Test
    void locationKeepsRemoteAndPlaceSegments() {
        assertThat(map(3).locationText()).isEqualTo("ONSITE | San Francisco, CA & New York City, NY");
        assertThat(map(4).locationText()).isEqualTo("REMOTE (Germany) | business-fluent German required");
        assertThat(map(7).locationText()).isNull();
    }

    @Test
    void rawPayloadIsTheCommentWithoutItsReplies() {
        JsonNode raw = CHILDREN.get(1);
        assertThat(raw.get("children")).hasSize(1);

        JsonNode payload = HnMapper.toPosting(dto(raw), raw).rawPayload();

        assertThat(payload.has("children")).isFalse();
        assertThat(payload.get("id").asLong()).isEqualTo(49922584L);
        assertThat(payload.get("text").asText()).isEqualTo(raw.get("text").asText());
        assertThat(raw.get("children")).hasSize(1); // the source node is not changed
    }

    @Test
    void deletedChildIsNotAPost() {
        HnCommentDto deleted = dto(CHILDREN.get(5));

        assertThat(deleted.id()).isEqualTo(50023607L);
        assertThat(HnMapper.isPost(deleted)).isFalse();
    }

    @Test
    void blankTextOrMissingAuthorIsNotAPost() {
        assertThat(HnMapper.isPost(comment(1L, "alice", "  "))).isFalse();
        assertThat(HnMapper.isPost(comment(1L, null, "Acme | Engineer"))).isFalse();
        assertThat(HnMapper.isPost(new HnCommentDto(1L, "story", "alice", "Acme | Engineer", null, null, null))).isFalse();
        assertThat(HnMapper.isPost(comment(1L, "alice", "Acme | Engineer"))).isTrue();
    }

    @Test
    void withoutAnExternalLinkTheApplyUrlIsThePermalink() {
        var posting = HnMapper.toPosting(comment(42L, "alice",
                "Acme | Engineer | REMOTE<p>Email jobs@acme.example or see <a href=\"https:&#x2F;&#x2F;news.ycombinator.com&#x2F;item?id=1\">"
                        + "https:&#x2F;&#x2F;news.ycombinator.com&#x2F;item?id=1</a>"), null);

        assertThat(posting.applyUrl()).isEqualTo("https://news.ycombinator.com/item?id=42");
    }

    @Test
    void aBareUrlInTheTextIsAnApplyUrl() {
        assertThat(HnMapper.applyUrl("Acme | Engineer<p>Apply at https://acme.example/jobs.")).contains("https://acme.example/jobs");
    }

    @Test
    void blankCompanyGetsAPlaceholderNamingTheComment() {
        var posting = HnMapper.toPosting(comment(50006929L, "bob",
                "<a href=\"https:&#x2F;&#x2F;grnh.se&#x2F;rlbk86k09us\" rel=\"nofollow\">https:&#x2F;&#x2F;grnh.se&#x2F;rlbk86k09us</a>"), null);

        assertThat(posting.companyName()).isEqualTo("Unknown (HN 50006929)");
        assertThat(posting.applyUrl()).isEqualTo("https://grnh.se/rlbk86k09us");
    }

    @Test
    void postedAtFallsBackToEpochSeconds() {
        var comment = new HnCommentDto(1L, "comment", "alice", "Acme | Engineer", null, 1790866927L, 2L);

        assertThat(HnMapper.postedAt(comment)).isEqualTo(Instant.parse("2026-10-01T15:02:07Z"));
    }

    @Test
    void missingIdOrUnreadableDateIsRejected() {
        assertThatThrownBy(() -> HnMapper.toPosting(comment(null, "alice", "Acme | Engineer"), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("missing id");
        var badDate = new HnCommentDto(1L, "comment", "alice", "Acme | Engineer", "yesterday", null, 2L);
        assertThatThrownBy(() -> HnMapper.toPosting(badDate, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("unreadable created_at 'yesterday'");
    }

    private static NormalizedPosting map(int index) {
        JsonNode raw = CHILDREN.get(index);
        return HnMapper.toPosting(dto(raw), raw);
    }

    private static HnCommentDto comment(Long id, String author, String text) {
        return new HnCommentDto(id, "comment", author, text, "2026-10-01T15:00:00.000Z", null, 49922569L);
    }

    private static HnCommentDto dto(JsonNode raw) {
        try {
            return Fixtures.JSON.treeToValue(raw, HnCommentDto.class);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
