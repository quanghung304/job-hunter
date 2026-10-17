package io.github.quanghung304.jobhunter.source.weworkremotely;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.common.model.RemoteHint;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeWorkRemotelyMapperTest {

    private static final List<WeWorkRemotelyItemDto> FULL_STACK =
            WeWorkRemotelyFeedParser.parse(Fixtures.text("weworkremotely/full-stack.xml"));
    private static final List<WeWorkRemotelyItemDto> ALL =
            WeWorkRemotelyFeedParser.parse(Fixtures.text("weworkremotely/remote-jobs.xml"));

    @ParameterizedTest(name = "{0}[{1}] {2}: {3}")
    @CsvSource(delimiter = '|', nullValues = "-", textBlock = """
            full-stack.xml  | 0 | Mixpanel         | Customer Value Architect III                         | mixpanel-customer-value-architect-iii-1                          | Anywhere in the World | Full-Time | full-stack programming | 2026-10-17T07:30:54Z
            full-stack.xml  | 1 | Fivetran         | Global Alliance Lead, Capgemini                      | fivetran-global-alliance-lead-capgemini                          | Anywhere in the World | Full-Time | full-stack programming | 2026-10-16T07:30:50Z
            full-stack.xml  | 2 | Askable          | Software Engineer, AI Coding Tasks (Contract)        | askable-software-engineer-ai-coding-tasks-contract               | Anywhere in the World | Contract  | full-stack programming | 2026-10-16T00:50:53Z
            full-stack.xml  | 3 | Medify           | Engineering Manager                                  | medify-engineering-manager                                       | Anywhere in the World | Full-Time | full-stack programming | 2026-10-15T13:59:07Z
            full-stack.xml  | 4 | Toptal           | Applied AI Engineers for telecommunications company  | toptal-applied-ai-engineers-for-telecommunications-company       | USA Only              | Full-Time | full-stack programming | 2026-10-12T16:38:01Z
            remote-jobs.xml | 0 | Legion           | Principal Product Manager, Agentic AI                | legion-principal-product-manager-agentic-ai                      | Anywhere in the World | Full-Time | product                | 2026-10-17T07:31:02Z
            remote-jobs.xml | 2 | Expel            | Senior Detection & Response Engineer                 | expel-senior-detection-response-engineer                         | Anywhere in the World | Full-Time | devops and sysadmin    | 2026-10-17T07:30:54Z
            """)
    void mapsEachFixtureItem(String file, int index, String company, String title, String slug, String region,
            String type, String category, Instant postedAt) {
        WeWorkRemotelyItemDto item = (file.equals("full-stack.xml") ? FULL_STACK : ALL).get(index);

        WeWorkRemotelyMapper.Mapped mapped = WeWorkRemotelyMapper.toPosting(item);
        NormalizedPosting posting = mapped.posting();

        String url = "https://weworkremotely.com/remote-jobs/" + slug;
        assertThat(mapped.warning()).isNull();
        assertThat(posting.sourceCode()).isEqualTo("weworkremotely");
        assertThat(posting.externalId()).isEqualTo(url);
        assertThat(posting.url()).isEqualTo(url);
        assertThat(posting.applyUrl()).isEqualTo(url);
        assertThat(posting.companyName()).isEqualTo(company);
        assertThat(posting.title()).isEqualTo(title);
        assertThat(posting.locationText()).isEqualTo(region);
        assertThat(posting.remoteHint()).isEqualTo(RemoteHint.REMOTE);
        assertThat(posting.employmentHint()).isEqualTo(type);
        assertThat(posting.tags()).containsExactly(category);
        assertThat(posting.postedAt()).isEqualTo(postedAt);
        assertThat(posting.salaryMin()).isNull();
        assertThat(posting.descriptionText()).isNotBlank()
                .doesNotContain("<p>", "<strong>", "</", "&lt;", "&amp;", "&nbsp;");
    }

    @Test
    void extraFieldsAreAppendedToTheDescriptionWithoutFlags() {
        String medify = WeWorkRemotelyMapper.toPosting(FULL_STACK.get(3)).posting().descriptionText();
        String askable = WeWorkRemotelyMapper.toPosting(FULL_STACK.get(2)).posting().descriptionText();

        assertThat(medify).endsWith("""


                Country: United Kingdom of Great Britain and Northern Ireland
                State: London
                Skills: React, Ruby on Rails, and Performance Management""");
        assertThat(askable).endsWith("\n\nState: Queensland\nSkills: C++, Java, Python, TypeScript, Go, and Rust")
                .doesNotContain("Country:");
        assertThat(WeWorkRemotelyMapper.toPosting(FULL_STACK.get(0)).posting().descriptionText())
                .doesNotContain("Country:", "State:", "Skills:");
    }

    @Test
    void rawPayloadHoldsEveryElementOfTheItem() {
        var raw = FULL_STACK.get(3).raw();

        assertThat(raw.get("title").asText()).isEqualTo("Medify: Engineering Manager");
        assertThat(raw.get("region").asText()).isEqualTo("Anywhere in the World");
        assertThat(raw.get("state").asText()).isEqualTo("London");
        assertThat(raw.get("expires_at").asText()).isNotBlank();
        assertThat(raw.get("media:content").get("@url").asText()).startsWith("https://");
        assertThat(raw.get("description").asText()).startsWith("<img");
    }

    @Test
    void titleWithoutCompanySeparatorIsKeptWithAWarning() {
        var item = withTitle(FULL_STACK.get(5), "Senior Backend Engineer");

        WeWorkRemotelyMapper.Mapped mapped = WeWorkRemotelyMapper.toPosting(item);

        assertThat(mapped.posting().companyName()).isEqualTo("Unknown");
        assertThat(mapped.posting().title()).isEqualTo("Senior Backend Engineer");
        assertThat(mapped.warning()).isEqualTo("title without \"Company: \": Senior Backend Engineer");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1} / {2}")
    @CsvSource(delimiter = '|', textBlock = """
            Acme Corp: Senior Backend Engineer       | Acme Corp | Senior Backend Engineer
            Fivetran : Global Alliance Lead          | Fivetran  | Global Alliance Lead
            Acme: Engineer: Platform                 | Acme      | Engineer: Platform
            Acme:Engineer                            | Unknown   | Acme:Engineer
            """)
    void splitsCompanyAndTitleOnTheFirstSeparator(String fullTitle, String company, String title) {
        var posting = WeWorkRemotelyMapper.toPosting(withTitle(FULL_STACK.get(5), fullTitle)).posting();

        assertThat(posting.companyName()).isEqualTo(company);
        assertThat(posting.title()).isEqualTo(title);
    }

    @Test
    void itemWithoutGuidAndLinkIsRejected() {
        var item = FULL_STACK.get(0);
        var broken = new WeWorkRemotelyItemDto(item.title(), null, null, item.descriptionHtml(), item.pubDate(),
                item.categories(), item.region(), null, null, null, item.type(), null, item.raw());

        assertThatThrownBy(() -> WeWorkRemotelyMapper.toPosting(broken))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("missing guid and link");
    }

    @Test
    void parserRejectsDoctypes() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <rss version="2.0"><channel><title>t</title><item><title>&x;</title></item></channel></rss>""";

        assertThatThrownBy(() -> WeWorkRemotelyFeedParser.parse(xxe))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unreadable XML");
    }

    private static WeWorkRemotelyItemDto withTitle(WeWorkRemotelyItemDto item, String title) {
        return new WeWorkRemotelyItemDto(title, item.link(), item.guid(), item.descriptionHtml(), item.pubDate(),
                item.categories(), item.region(), item.country(), item.state(), item.skills(), item.type(),
                item.expiresAt(), item.raw());
    }
}
