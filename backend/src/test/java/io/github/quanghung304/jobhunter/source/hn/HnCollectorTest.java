package io.github.quanghung304.jobhunter.source.hn;

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
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.MutableClock;
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

class HnCollectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String SEARCH = "/api/v1/search_by_date";
    private static final String OCTOBER = "/api/v1/items/49922569";
    private static final String SEPTEMBER = "/api/v1/items/49522897";
    /** The 11 job comments of the October fixture, in thread order (the deleted child is skipped). */
    private static final List<String> OCTOBER_IDS = List.of("49922609", "49922584", "49925526", "49922593",
            "49922616", "49922881", "49924674", "49922929", "49922899", "49928066", "49930656");
    /** Nested replies of PrairieLearn and DuckDuckGo in the October fixture. */
    private static final List<String> REPLY_IDS = List.of("49928484", "49934236");

    private final List<Duration> pauses = new ArrayList<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-22T10:00:00Z"));

    @Test
    void collectsTheTopLevelCommentsOfTheLatestThread() {
        stubAll();

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(12);
        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .containsExactlyElementsOf(OCTOBER_IDS)
                .doesNotContainAnyElementsOf(REPLY_IDS);
        assertThat(pauses).containsExactly(Duration.ofSeconds(1));
        wm.verify(1, getRequestedFor(urlPathEqualTo(SEARCH))
                .withQueryParam("tags", equalTo("story,author_whoishiring"))
                .withQueryParam("hitsPerPage", equalTo("10")));
        wm.verify(1, getRequestedFor(urlPathEqualTo(OCTOBER)));
        wm.verify(0, getRequestedFor(urlPathEqualTo(SEPTEMBER)));
    }

    @Test
    void duringTheFirstDaysOfAMonthThePreviousThreadIsFetchedToo() {
        stubAll();
        clock.set(Instant.parse("2026-10-07T23:00:00Z"));

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(14);
        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .startsWith(OCTOBER_IDS.toArray(String[]::new))
                .endsWith("49522903", "49522912")
                .doesNotHaveDuplicates();
        assertThat(pauses).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void beforeThisMonthsThreadIsPostedOnlyTheLatestIsFetched() {
        stubAll();
        clock.set(Instant.parse("2026-11-01T09:00:00Z")); // the November thread comes at 15:00 UTC

        collector().collect(request(null, 500));

        wm.verify(1, getRequestedFor(urlPathEqualTo(OCTOBER)));
        wm.verify(0, getRequestedFor(urlPathEqualTo(SEPTEMBER)));
    }

    @Test
    void reRunningOnTheSameThreadReturnsTheSameIds() {
        stubAll();
        HnCollector collector = collector();

        List<String> first = ids(collector.collect(request(null, 500)));
        List<String> second = ids(collector.collect(request(null, 500)));

        assertThat(second).isEqualTo(first).doesNotHaveDuplicates();
    }

    @Test
    void skipsCommentsOlderThanSinceButReadsTheWholeThread() {
        stubAll();

        CollectResult result = collector().collect(request(Instant.parse("2026-10-01T18:00:00Z"), 500));

        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .containsExactly("49925526", "49928066", "49930656");
        assertThat(result.fetched()).isEqualTo(12);
    }

    @Test
    void stopsAtMaxItems() {
        stubAll();
        clock.set(Instant.parse("2026-10-03T10:00:00Z"));

        CollectResult result = collector().collect(request(null, 3));

        assertThat(result.postings()).hasSize(3);
        wm.verify(0, getRequestedFor(urlPathEqualTo(SEPTEMBER)));
    }

    @Test
    void searchFailureFailsTheRun() {
        wm.stubFor(get(urlPathEqualTo(SEARCH)).willReturn(serverError()));

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("thread search: HTTP 500 Internal Server Error");
    }

    @Test
    void noHiringThreadFailsTheRun() {
        ObjectNode search = Fixtures.json("hn-whoishiring/search-by-date.json").deepCopy();
        ArrayNode hits = (ArrayNode) search.get("hits");
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).get("title").asText().startsWith("Ask HN: Who is hiring?")) {
                hits.remove(i);
            }
        }
        stub(SEARCH, okJson(search.toString()));

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("thread search: no 'Ask HN: Who is hiring?' story found");
    }

    @Test
    void aFailingPreviousThreadIsAWarning() {
        stubAll();
        stub(SEPTEMBER, serverError());
        clock.set(Instant.parse("2026-10-02T08:00:00Z"));

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(11);
        assertThat(result.warnings()).containsExactly("thread 49522897: HTTP 500 Internal Server Error");
    }

    @Test
    void failingEveryThreadFetchFailsTheRun() {
        stubAll();
        stub(OCTOBER, serverError());

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("thread 49922569: HTTP 500 Internal Server Error");
    }

    @Test
    void malformedCommentsAreWarnings() {
        ObjectNode item = Fixtures.json("hn-whoishiring/item-october-2026.json").deepCopy();
        ArrayNode children = (ArrayNode) item.get("children");
        ((ObjectNode) children.get(0)).put("id", "not-a-number");
        ((ObjectNode) children.get(2)).put("created_at", "yesterday").remove("created_at_i");
        stub(SEARCH, okJson(Fixtures.text("hn-whoishiring/search-by-date.json")));
        stub(OCTOBER, okJson(item.toString()));

        CollectResult result = collector().collect(request(null, 500));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(9);
        assertThat(result.warnings()).hasSize(2)
                .anySatisfy(w -> assertThat(w).startsWith("thread 49922569 item not-a-number: unreadable item"))
                .anySatisfy(w -> assertThat(w).isEqualTo("thread 49922569 item 49925526: unreadable created_at 'yesterday'"));
    }

    @Test
    void threadsAreTheNewestHiringStoriesOnly() {
        var hits = List.of(
                hit("3", "Ask HN: Who wants to be hired? (October 2026)", "2026-10-01T15:02:07Z"),
                hit("2", "Ask HN: Who is hiring? (September 2026)", "2026-09-01T15:01:17Z"),
                hit("1", "Ask HN: Who is hiring? (October 2026)", "2026-10-01T15:02:07Z"),
                hit("0", "Ask HN: Who is hiring? (August 2026)", "2026-08-03T15:00:54Z"));

        assertThat(HnCollector.threadsToFetch(hits, Instant.parse("2026-10-08T00:00:00Z"), 7))
                .extracting(HnCollector.Story::id).containsExactly("1");
        assertThat(HnCollector.threadsToFetch(hits, Instant.parse("2026-10-07T23:59:59Z"), 7))
                .extracting(HnCollector.Story::id).containsExactly("1", "2");
        assertThat(HnCollector.threadsToFetch(hits, Instant.parse("2026-10-07T23:59:59Z"), 0))
                .extracting(HnCollector.Story::id).containsExactly("1");
        assertThat(HnCollector.threadsToFetch(List.of(hits.get(0)), Instant.parse("2026-10-02T00:00:00Z"), 7)).isEmpty();
    }

    private HnCollector collector() {
        var http = new HttpClients(RestClient.builder(), new HttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(5)), "");
        var properties = new HnProperties(wm.baseUrl(), Duration.ofSeconds(1), 10, 7);
        return new HnCollector(http, new Politeness(pauses::add), Fixtures.JSON, properties, clock);
    }

    private static CollectRequest request(Instant since, int maxItems) {
        return new CollectRequest(since, List.of(), maxItems);
    }

    private static List<String> ids(CollectResult result) {
        return result.postings().stream().map(NormalizedPosting::externalId).toList();
    }

    private static HnSearchDto.Hit hit(String id, String title, String createdAt) {
        return new HnSearchDto.Hit(id, title, createdAt, null);
    }

    /** Real captures: the story search, the October thread (trimmed) and the September thread (2 comments). */
    private static void stubAll() {
        stub(SEARCH, okJson(Fixtures.text("hn-whoishiring/search-by-date.json")));
        stub(OCTOBER, okJson(Fixtures.text("hn-whoishiring/item-october-2026.json")));
        stub(SEPTEMBER, okJson(Fixtures.text("hn-whoishiring/item-september-2026.json")));
    }

    private static void stub(String path, ResponseDefinitionBuilder response) {
        wm.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }
}
