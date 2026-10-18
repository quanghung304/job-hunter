package io.github.quanghung304.jobhunter.source.jobicy;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.HttpProperties;
import io.github.quanghung304.jobhunter.source.Politeness;
import io.github.quanghung304.jobhunter.common.Sleeper;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Opt-in, three real requests (the default queries): {@code ./mvnw verify -Plive}. */
@Tag("live")
class JobicyCollectorLiveTest {

    @Test
    void defaultQueriesReturnUniqueMappedPostings() {
        var http = new HttpClients(RestClient.builder(),
                new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), System.getenv("OWNER_CONTACT_EMAIL"));
        var collector = new JobicyCollector(http, new Politeness(Sleeper.SYSTEM), new ObjectMapper(),
                new JobicyProperties("https://jobicy.com", null, 50, Duration.ofSeconds(2), 1));

        CollectResult result = collector.collect(new CollectRequest(null, List.of(), 200));

        assertThat(result.status()).isNotEqualTo(CollectStatus.FAILED);
        assertThat(result.postings()).isNotEmpty().allSatisfy(p -> {
            assertThat(p.url()).startsWith("https://jobicy.com/jobs/");
            assertThat(p.title()).isNotBlank();
            assertThat(p.postedAt()).isNotNull();
            assertThat(p.descriptionText()).doesNotContain("<p>");
        });
        var ids = new HashSet<String>();
        assertThat(result.postings()).allMatch(p -> ids.add(p.externalId()));
    }
}
