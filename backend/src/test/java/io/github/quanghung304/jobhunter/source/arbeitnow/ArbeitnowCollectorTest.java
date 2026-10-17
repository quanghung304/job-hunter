package io.github.quanghung304.jobhunter.source.arbeitnow;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
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

class ArbeitnowCollectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String PATH = "/api/job-board-api";
    private static final String CONTACT = "owner@example.com";

    private final List<Duration> pauses = new ArrayList<>();

    @Test
    void pagesUntilAnEmptyPageWithoutNext() {
        stubFixturePages();

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(9);
        assertThat(result.postings()).hasSize(9);
        assertThat(result.warnings()).isEmpty();
        assertThat(result.errorMessage()).isNull();
        assertThat(pauses).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
        assertPagesRequested(1, 2, 3);
    }

    @Test
    void remoteOnlySkipsNonRemoteItemsButCountsThemAsFetched() {
        stubFixturePages();

        CollectResult result = collector(true, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.fetched()).isEqualTo(9);
        assertThat(result.postings()).extracting(NormalizedPosting::externalId).containsExactly(
                "remote-interim-senior-bi-analyst-all-genders-germany-169194",
                "full-stack-engineer-ai-authoring-platform-hamburg-483900",
                "remote-head-of-urology-232681",
                "remote-engineering-manager-452658");
    }

    @Test
    void remoteOnlyKeepsItemsWhoseLocationSaysRemote() {
        ObjectNode page = withoutNext(Fixtures.json("arbeitnow/page1.json"));
        // Real quirk: "remote": false with "location": "Remote".
        ((ObjectNode) page.get("data").get(4)).put("location", "Remote");
        stubPage(1, okJson(page.toString()));

        CollectResult result = collector(true, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .contains("product-manager-homepage-london-355976")
                .hasSize(4);
    }

    @Test
    void skipsItemsOlderThanSinceAndStopsWhenAWholePageIsOlder() {
        stubFixturePages();
        Instant since = Instant.ofEpochSecond(1_792_190_000L); // page 1 is mixed, page 2 is older

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(since, 200));

        assertThat(result.postings()).extracting(NormalizedPosting::postedAt)
                .allMatch(postedAt -> !postedAt.isBefore(since))
                .hasSize(4);
        assertPagesRequested(1, 2);
    }

    @Test
    void stopsAtMaxItems() {
        stubFixturePages();

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 4));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.postings()).hasSize(4);
        assertThat(result.fetched()).isEqualTo(4);
        assertPagesRequested(1);
    }

    @Test
    void stopsAtMaxPages() {
        stubFixturePages();

        CollectResult result = collector(false, 1, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.postings()).hasSize(6);
        assertPagesRequested(1);
        assertThat(pauses).isEmpty();
    }

    @Test
    void stopsWhenNextIsNull() {
        stubPage(1, okJson(withoutNext(Fixtures.json("arbeitnow/page1.json")).toString()));

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.postings()).hasSize(6);
        assertPagesRequested(1);
    }

    @Test
    void emptyFirstPageIsASuccessWithNothing() {
        stubPage(1, okJson(Fixtures.text("arbeitnow/last-page.json")));

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.postings()).isEmpty();
        assertThat(result.fetched()).isZero();
    }

    @Test
    void serverErrorOnFirstPageFails() {
        stubPage(1, serverError().withBody("oops"));

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).contains("page 1").contains("500");
        assertThat(result.postings()).isEmpty();
    }

    @Test
    void timeoutOnFirstPageFails() {
        // Own server: the delayed request would otherwise be logged into the next test's journal.
        var slow = new WireMockServer(wireMockConfig().dynamicPort());
        slow.start();
        try {
            slow.stubFor(get(urlPathEqualTo(PATH)).willReturn(
                    okJson(Fixtures.text("arbeitnow/page1.json")).withFixedDelay(1_500)));

            CollectResult result = collector(slow.baseUrl(), false, 20, Duration.ofMillis(300))
                    .collect(request(null, 200));

            assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
            assertThat(result.errorMessage()).startsWith("page 1:");
            assertThat(result.postings()).isEmpty();
        } finally {
            slow.stop();
        }
    }

    @Test
    void nonJsonResponseFails() {
        stubPage(1, WireMock.aResponse().withStatus(200).withHeader("Content-Type", "text/html")
                .withBody("<html>maintenance</html>"));

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
    }

    @Test
    void laterPageErrorKeepsEarlierPostingsAsPartial() {
        stubPage(1, okJson(Fixtures.text("arbeitnow/page1.json")));
        stubPage(2, serverError());

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(6);
        assertThat(result.warnings()).singleElement().asString().startsWith("page 2 failed");
    }

    @Test
    void malformedItemsAreWarningsAndTheRunIsPartial() {
        ObjectNode page = withoutNext(Fixtures.json("arbeitnow/page1.json"));
        ArrayNode items = (ArrayNode) page.get("data");
        ((ObjectNode) items.get(0)).remove("slug");
        ((ObjectNode) items.get(1)).put("created_at", "yesterday");
        stubPage(1, okJson(page.toString()));

        CollectResult result = collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.fetched()).isEqualTo(6);
        assertThat(result.postings()).hasSize(4);
        assertThat(result.warnings()).hasSize(2)
                .anySatisfy(w -> assertThat(w).contains("(no slug)").contains("missing slug"))
                .anySatisfy(w -> assertThat(w).contains("qc-inspector-germany-326632").contains("unreadable item"));
    }

    @Test
    void sendsThePoliteUserAgent() {
        stubPage(1, okJson(Fixtures.text("arbeitnow/last-page.json")));

        collector(false, 20, Duration.ofSeconds(5)).collect(request(null, 200));

        wm.verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("User-Agent", equalTo("job-hunter/0.1 (+personal use; contact: " + CONTACT + ")")));
    }

    private ArbeitnowCollector collector(boolean remoteOnly, int maxPages, Duration readTimeout) {
        return collector(wm.baseUrl(), remoteOnly, maxPages, readTimeout);
    }

    private ArbeitnowCollector collector(String baseUrl, boolean remoteOnly, int maxPages, Duration readTimeout) {
        var http = new HttpClients(RestClient.builder(), new HttpProperties(Duration.ofSeconds(2), readTimeout), CONTACT);
        var properties = new ArbeitnowProperties(baseUrl, remoteOnly, Duration.ofSeconds(1), maxPages);
        return new ArbeitnowCollector(http, new Politeness(pauses::add), Fixtures.JSON, properties);
    }

    private static CollectRequest request(Instant since, int maxItems) {
        return new CollectRequest(since, List.of(), maxItems);
    }

    /** page 1 → 6 items, page 2 → 3 older items (captured from page 6), page 3 → empty, next null. */
    private static void stubFixturePages() {
        stubPage(1, okJson(Fixtures.text("arbeitnow/page1.json")));
        stubPage(2, okJson(Fixtures.text("arbeitnow/page2.json")));
        stubPage(3, okJson(Fixtures.text("arbeitnow/last-page.json")));
    }

    private static void stubPage(int page, ResponseDefinitionBuilder response) {
        wm.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("page", equalTo(String.valueOf(page))).willReturn(response));
    }

    private static void assertPagesRequested(int... pages) {
        var requested = wm.findAll(getRequestedFor(urlPathEqualTo(PATH))).stream()
                .map(r -> Integer.parseInt(r.queryParameter("page").firstValue()))
                .toList();
        assertThat(requested).containsExactly(java.util.Arrays.stream(pages).boxed().toArray(Integer[]::new));
    }

    private static ObjectNode withoutNext(com.fasterxml.jackson.databind.JsonNode page) {
        ObjectNode copy = page.deepCopy();
        ((ObjectNode) copy.get("links")).putNull("next");
        return copy;
    }
}
