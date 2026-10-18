package io.github.quanghung304.jobhunter.source.himalayas;

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

/** Opt-in, two real requests: {@code ./mvnw verify -Plive}. */
@Tag("live")
class HimalayasCollectorLiveTest {

    @Test
    void fetchesAndMapsTwoPages() {
        var http = new HttpClients(RestClient.builder(),
                new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(20)), System.getenv("OWNER_CONTACT_EMAIL"));
        var collector = new HimalayasCollector(http, new Politeness(Sleeper.SYSTEM), new ObjectMapper(),
                new HimalayasProperties("https://himalayas.app", System.getenv("HIMALAYAS_API_KEY"), 20,
                        Duration.ofSeconds(2), 2));

        CollectResult result = collector.collect(new CollectRequest(null, List.of(), 40));

        assertThat(result.status()).isNotEqualTo(CollectStatus.FAILED);
        assertThat(result.postings()).hasSizeGreaterThan(20).allSatisfy(p -> {
            assertThat(p.url()).startsWith("https://himalayas.app/companies/");
            assertThat(p.applyUrl()).startsWith("https://himalayas.app/");
            assertThat(p.title()).isNotBlank();
            assertThat(p.locationText()).isNotBlank();
            assertThat(p.descriptionText()).contains("Timezone restrictions: ").doesNotContain("<p>");
            assertThat(p.postedAt()).isNotNull();
        });
    }
}
