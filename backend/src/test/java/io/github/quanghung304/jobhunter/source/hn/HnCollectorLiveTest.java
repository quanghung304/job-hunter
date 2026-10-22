package io.github.quanghung304.jobhunter.source.hn;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.Sleeper;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.HttpProperties;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.Politeness;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Opt-in, two or three real requests (search + latest thread): {@code ./mvnw verify -Plive}. */
@Tag("live")
class HnCollectorLiveTest {

    @Test
    void latestThreadReturnsUniqueMappedPostings() {
        var http = new HttpClients(RestClient.builder(),
                new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), System.getenv("OWNER_CONTACT_EMAIL"));
        var collector = new HnCollector(http, new Politeness(Sleeper.SYSTEM), new ObjectMapper(),
                new HnProperties("https://hn.algolia.com", Duration.ofSeconds(1), 10, 7), Clock.systemUTC());

        CollectResult result = collector.collect(new CollectRequest(null, List.of(), 1000));

        assertThat(result.status()).isNotEqualTo(CollectStatus.FAILED);
        assertThat(result.postings()).hasSizeGreaterThan(50).allSatisfy(p -> {
            assertThat(p.url()).startsWith("https://news.ycombinator.com/item?id=" + p.externalId());
            assertThat(p.title()).isNotBlank();
            assertThat(p.companyName()).isNotBlank();
            assertThat(p.postedAt()).isNotNull();
            assertThat(p.descriptionText()).isNotBlank().doesNotContain("<p>");
        });
        assertThat(result.postings().stream().filter(p -> !p.title().equals(HnHeaderParser.NO_TITLE)).count())
                .as("most headers name a role").isGreaterThan(result.postings().size() / 2);
        var ids = new HashSet<String>();
        assertThat(result.postings()).extracting(NormalizedPosting::externalId).allMatch(ids::add);
    }
}
