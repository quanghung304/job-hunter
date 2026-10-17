package io.github.quanghung304.jobhunter.source.weworkremotely;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.HttpProperties;
import io.github.quanghung304.jobhunter.source.Politeness;
import io.github.quanghung304.jobhunter.source.Sleeper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Opt-in, three real feed requests (the default feeds): {@code ./mvnw verify -Plive}. */
@Tag("live")
class WeWorkRemotelyCollectorLiveTest {

    @Test
    void defaultFeedsParseAndMap() {
        var http = new HttpClients(RestClient.builder(),
                new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), System.getenv("OWNER_CONTACT_EMAIL"));
        var collector = new WeWorkRemotelyCollector(http, new Politeness(Sleeper.SYSTEM),
                new WeWorkRemotelyProperties("https://weworkremotely.com", null, Duration.ofSeconds(2)));

        CollectResult result = collector.collect(new CollectRequest(null, List.of(), 500));

        assertThat(result.status()).isNotEqualTo(CollectStatus.FAILED);
        assertThat(result.postings()).isNotEmpty().allSatisfy(p -> {
            assertThat(p.url()).startsWith("https://weworkremotely.com/remote-jobs/");
            assertThat(p.title()).isNotBlank();
            assertThat(p.companyName()).isNotBlank();
            assertThat(p.locationText()).isNotBlank();
            assertThat(p.postedAt()).isNotNull();
            assertThat(p.descriptionText()).doesNotContain("<p>", "&lt;");
        });
    }
}
