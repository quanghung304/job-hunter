package io.github.quanghung304.jobhunter.source.arbeitnow;

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
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Opt-in, calls the real API once: {@code ./mvnw verify -Plive}. */
@Tag("live")
class ArbeitnowCollectorLiveTest {

    @Test
    void fetchesAndMapsTheFirstPage() {
        var http = new HttpClients(RestClient.builder(),
                new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(20)), System.getenv("OWNER_CONTACT_EMAIL"));
        var collector = new ArbeitnowCollector(http, new Politeness(Sleeper.SYSTEM), new ObjectMapper(),
                new ArbeitnowProperties("https://www.arbeitnow.com", false, Duration.ofSeconds(1), 1));

        CollectResult result = collector.collect(new CollectRequest(null, List.of(), 20));

        assertThat(result.status()).isNotEqualTo(CollectStatus.FAILED);
        assertThat(result.postings()).isNotEmpty().allSatisfy(p -> {
            assertThat(p.externalId()).isNotBlank();
            assertThat(p.url()).startsWith("https://");
            assertThat(p.title()).isNotBlank();
            assertThat(p.descriptionText()).doesNotContain("<p>", "&lt;");
            assertThat(p.postedAt()).isNotNull();
        });
    }
}
