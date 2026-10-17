package io.github.quanghung304.jobhunter.source.weworkremotely;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.CollectStatus;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.HttpProperties;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.Politeness;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class WeWorkRemotelyCollectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String FULL_STACK = "/categories/remote-full-stack-programming-jobs.rss";
    private static final String ALL = "/remote-jobs.rss";

    private final List<Duration> pauses = new ArrayList<>();

    @Test
    void sameGuidInTwoFeedsIsOnePosting() {
        stubFeeds();

        CollectResult result = collector(FULL_STACK, ALL).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(10);
        assertThat(result.postings()).hasSize(8).extracting(NormalizedPosting::externalId).doesNotHaveDuplicates();
        assertThat(pauses).containsExactly(Duration.ofSeconds(2));
        wm.verify(getRequestedFor(urlPathEqualTo(FULL_STACK)).withHeader("Accept", containing("application/rss+xml")));
    }

    @Test
    void titleWithoutCompanyIsIngestedWithAWarning() {
        String xml = Fixtures.text("weworkremotely/full-stack.xml")
                .replace("<title>Vercel: Software Engineer, Backend</title>", "<title>Software Engineer, Backend</title>");
        stub(FULL_STACK, xmlResponse(xml));

        CollectResult result = collector(FULL_STACK).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(6)
                .anySatisfy(p -> {
                    assertThat(p.companyName()).isEqualTo("Unknown");
                    assertThat(p.title()).isEqualTo("Software Engineer, Backend");
                });
        assertThat(result.warnings()).singleElement().asString()
                .contains("vercel-software-engineer-backend").contains("title without \"Company: \"");
    }

    @Test
    void itemsOlderThanSinceAreSkipped() {
        stubFeeds();
        Instant since = Instant.parse("2026-10-16T00:00:00Z");

        CollectResult result = collector(FULL_STACK, ALL).collect(request(since, 200));

        assertThat(result.postings()).allMatch(p -> !p.postedAt().isBefore(since))
                .extracting(NormalizedPosting::companyName)
                .containsExactly("Mixpanel", "Fivetran", "Askable", "Legion", "Expel");
    }

    @Test
    void stopsAtMaxItems() {
        stubFeeds();

        CollectResult result = collector(FULL_STACK, ALL).collect(request(null, 3));

        assertThat(result.postings()).hasSize(3);
        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(ALL)))).isEmpty();
    }

    @Test
    void oneFailingFeedIsAWarning() {
        stub(FULL_STACK, serverError());
        stub(ALL, xmlResponse(Fixtures.text("weworkremotely/remote-jobs.xml")));

        CollectResult result = collector(FULL_STACK, ALL).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(4);
        assertThat(result.warnings()).containsExactly("feed " + FULL_STACK + ": HTTP 500 Internal Server Error");
    }

    @Test
    void cloudflareChallengePageIsAFeedFailure() {
        stub(FULL_STACK, aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                .withBody("<!DOCTYPE html><html><head><title>Just a moment...</title></head><body></body></html>"));

        CollectResult result = collector(FULL_STACK).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).startsWith("feed " + FULL_STACK + ": ");
    }

    @Test
    void allFeedsFailingIsAFailedRun() {
        stub(FULL_STACK, aResponse().withStatus(403));
        stub(ALL, aResponse().withStatus(403));

        CollectResult result = collector(FULL_STACK, ALL).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("feed " + FULL_STACK + ": HTTP 403 Forbidden");
    }

    @Test
    void defaultFeedsAreTheVerifiedCategories() {
        assertThat(new WeWorkRemotelyProperties("https://weworkremotely.com", null, Duration.ofSeconds(2)).feeds())
                .containsExactly("/categories/remote-back-end-programming-jobs.rss",
                        "/categories/remote-full-stack-programming-jobs.rss",
                        "/categories/remote-devops-sysadmin-jobs.rss");
    }

    private WeWorkRemotelyCollector collector(String... feeds) {
        var http = new HttpClients(RestClient.builder(), new HttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(5)), "");
        var properties = new WeWorkRemotelyProperties(wm.baseUrl(), List.of(feeds), Duration.ofSeconds(2));
        return new WeWorkRemotelyCollector(http, new Politeness(pauses::add), properties);
    }

    private static CollectRequest request(Instant since, int maxItems) {
        return new CollectRequest(since, List.of(), maxItems);
    }

    /** Real captures: full-stack (6 items) and the all-jobs feed (4 items), 2 shared guids. */
    private static void stubFeeds() {
        stub(FULL_STACK, xmlResponse(Fixtures.text("weworkremotely/full-stack.xml")));
        stub(ALL, xmlResponse(Fixtures.text("weworkremotely/remote-jobs.xml")));
    }

    private static ResponseDefinitionBuilder xmlResponse(String xml) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/rss+xml; charset=utf-8").withBody(xml);
    }

    private static void stub(String path, ResponseDefinitionBuilder response) {
        wm.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }
}
