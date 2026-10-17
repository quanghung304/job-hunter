package io.github.quanghung304.jobhunter.source.himalayas;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
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
import com.github.tomakehurst.wiremock.client.MappingBuilder;
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

class HimalayasCollectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String PATH = "/jobs/api";
    /** nextCursor values of the captured pages. */
    private static final String CURSOR_1 = Fixtures.json("himalayas/page1.json").get("nextCursor").asText();
    private static final String CURSOR_2 = Fixtures.json("himalayas/page2.json").get("nextCursor").asText();

    private final List<Duration> pauses = new ArrayList<>();

    @Test
    void followsCursorsUntilAResponseHasNoNextCursor() {
        stubFixturePages();

        CollectResult result = collector(null, 25).collect(request(null, 400));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(12);
        assertThat(result.postings()).hasSize(12);
        assertThat(pauses).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(2));
        wm.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("limit", equalTo("20"))
                .withQueryParam("cursor", absent()));
        wm.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("cursor", equalTo(CURSOR_1)));
        wm.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("cursor", equalTo(CURSOR_2)));
    }

    @Test
    void skipsJobsOlderThanSinceAndStopsWhenAWholePageIsOlder() {
        stubFixturePages();
        // page 1: 1792249212..1792249654, page 2: ..1792249185, last page: worldwide, much older
        Instant since = Instant.ofEpochSecond(1_792_249_300L);

        CollectResult result = collector(null, 25).collect(request(since, 400));

        assertThat(result.postings()).extracting(NormalizedPosting::postedAt)
                .hasSize(4)
                .allMatch(postedAt -> !postedAt.isBefore(since));
        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(PATH)))).hasSize(2);
    }

    @Test
    void stopsAtMaxItems() {
        stubFixturePages();

        CollectResult result = collector(null, 25).collect(request(null, 8));

        assertThat(result.postings()).hasSize(8);
        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(PATH)))).hasSize(2);
    }

    @Test
    void stopsAtMaxPages() {
        stubFixturePages();

        CollectResult result = collector(null, 1).collect(request(null, 400));

        assertThat(result.postings()).hasSize(6);
        assertThat(pauses).isEmpty();
    }

    @Test
    void stopsOnAnEmptyPage() {
        ObjectNode empty = Fixtures.json("himalayas/page1.json").deepCopy();
        ((ArrayNode) empty.get("jobs")).removeAll(); // nextCursor kept: the empty page alone must stop the walk
        stub(firstPage(), okJson(empty.toString()));

        CollectResult result = collector(null, 25).collect(request(null, 400));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.postings()).isEmpty();
        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(PATH)))).hasSize(1);
    }

    @Test
    void sendsTheApiKeyOnlyWhenConfigured() {
        stub(firstPage(), okJson(Fixtures.text("himalayas/last-page.json")));

        collector("test-key", 25).collect(request(null, 400));
        collector(null, 25).collect(request(null, 400));

        var requests = wm.findAll(getRequestedFor(urlPathEqualTo(PATH)));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(requests.get(1).containsHeader("Authorization")).isFalse();
    }

    @Test
    void serverErrorOnFirstPageFails() {
        stub(firstPage(), serverError());

        CollectResult result = collector(null, 25).collect(request(null, 400));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("page 1: HTTP 500 Internal Server Error");
    }

    @Test
    void rateLimitOnALaterPageKeepsEarlierPostings() {
        stub(firstPage(), okJson(Fixtures.text("himalayas/page1.json")));
        stub(cursorPage(CURSOR_1), WireMock.aResponse().withStatus(429).withHeader("Retry-After", "30"));

        CollectResult result = collector(null, 25).collect(request(null, 400));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(6);
        assertThat(result.warnings()).containsExactly("page 2 failed, kept earlier pages: HTTP 429 Too Many Requests");
    }

    @Test
    void malformedJobsAreWarnings() {
        ObjectNode page = Fixtures.json("himalayas/last-page.json").deepCopy();
        ((ObjectNode) page.get("jobs").get(0)).remove("guid");
        ((ObjectNode) page.get("jobs").get(1)).put("pubDate", "yesterday");
        stub(firstPage(), okJson(page.toString()));

        CollectResult result = collector(null, 25).collect(request(null, 400));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.postings()).hasSize(1);
        assertThat(result.warnings()).hasSize(2);
    }

    @Test
    void duplicateGuidsInOneRunAreSkipped() {
        ObjectNode page = Fixtures.json("himalayas/last-page.json").deepCopy();
        ((ArrayNode) page.get("jobs")).add(page.get("jobs").get(0).deepCopy());
        stub(firstPage(), okJson(page.toString()));

        CollectResult result = collector(null, 25).collect(request(null, 400));

        assertThat(result.fetched()).isEqualTo(4);
        assertThat(result.postings()).hasSize(3);
    }

    private HimalayasCollector collector(String apiKey, int maxPages) {
        var http = new HttpClients(RestClient.builder(), new HttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(5)), "");
        var properties = new HimalayasProperties(wm.baseUrl(), apiKey, 20, Duration.ofSeconds(2), maxPages);
        return new HimalayasCollector(http, new Politeness(pauses::add), Fixtures.JSON, properties);
    }

    private static CollectRequest request(Instant since, int maxItems) {
        return new CollectRequest(since, List.of(), maxItems);
    }

    /** first page → page1 (6 jobs), cursor 1 → page2 (3 jobs), cursor 2 → last page (3 worldwide jobs, no cursor). */
    private static void stubFixturePages() {
        stub(firstPage(), okJson(Fixtures.text("himalayas/page1.json")));
        stub(cursorPage(CURSOR_1), okJson(Fixtures.text("himalayas/page2.json")));
        stub(cursorPage(CURSOR_2), okJson(Fixtures.text("himalayas/last-page.json")));
    }

    private static MappingBuilder firstPage() {
        return get(urlPathEqualTo(PATH)).withQueryParam("cursor", absent());
    }

    private static MappingBuilder cursorPage(String cursor) {
        return get(urlPathEqualTo(PATH)).withQueryParam("cursor", equalTo(cursor));
    }

    private static void stub(MappingBuilder request, ResponseDefinitionBuilder response) {
        wm.stubFor(request.willReturn(response));
    }
}
