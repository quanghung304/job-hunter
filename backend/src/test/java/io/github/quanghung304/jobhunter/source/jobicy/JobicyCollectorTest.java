package io.github.quanghung304.jobhunter.source.jobicy;

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

class JobicyCollectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String PATH = "/api/v2/remote-jobs";
    private static final JobicyProperties.Query EUROPE = new JobicyProperties.Query("europe", "engineering", null);
    private static final JobicyProperties.Query JAVA = new JobicyProperties.Query(null, null, "java");

    private final List<Duration> pauses = new ArrayList<>();

    @Test
    void overlappingQueriesProduceUniquePostings() {
        stubQueries();

        CollectResult result = collector(List.of(EUROPE, JAVA), 3).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(10);
        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .containsExactly("155587", "155579", "155565", "153452", "155439", "153449", "155488", "155437")
                .doesNotHaveDuplicates();
        assertThat(pauses).containsExactly(Duration.ofSeconds(2));
        wm.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("count", equalTo("100"))
                .withQueryParam("geo", equalTo("europe")).withQueryParam("industry", equalTo("engineering"))
                .withQueryParam("tag", absent()).withQueryParam("cursor", absent()));
        wm.verify(getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("tag", equalTo("java"))
                .withQueryParam("geo", absent()));
    }

    @Test
    void eachQueryStopsAtItsFirstJobOlderThanSince() {
        stubQueries();
        Instant since = Instant.parse("2026-10-17T00:00:00Z");

        CollectResult result = collector(List.of(EUROPE, JAVA), 3).collect(request(since, 200));

        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .containsExactly("155587", "155579", "155565", "153452", "153449");
        assertThat(result.postings()).allMatch(p -> !p.postedAt().isBefore(since));
    }

    @Test
    void stopsTheWholeRunAtMaxItems() {
        stubQueries();

        CollectResult result = collector(List.of(EUROPE, JAVA), 3).collect(request(null, 3));

        assertThat(result.postings()).hasSize(3);
        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(PATH)))).hasSize(1);
    }

    @Test
    void followsTheCursorWhileHasMore() {
        ObjectNode first = Fixtures.json("jobicy/europe-engineering.json").deepCopy();
        first.put("nextCursor", "eyJ0IjoxfQ").put("hasMore", true);
        stub(query(EUROPE).withQueryParam("cursor", absent()), okJson(first.toString()));
        stub(query(EUROPE).withQueryParam("cursor", equalTo("eyJ0IjoxfQ")),
                okJson(Fixtures.text("jobicy/tag-java.json")));

        CollectResult result = collector(List.of(EUROPE), 3).collect(request(null, 200));

        assertThat(result.postings()).hasSize(8);
        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(PATH)))).hasSize(2);
    }

    @Test
    void stopsAtMaxPagesPerQuery() {
        ObjectNode looping = Fixtures.json("jobicy/europe-engineering.json").deepCopy();
        looping.put("nextCursor", "again").put("hasMore", true);
        stub(query(EUROPE), okJson(looping.toString()));

        collector(List.of(EUROPE), 2).collect(request(null, 200));

        assertThat(wm.findAll(getRequestedFor(urlPathEqualTo(PATH)))).hasSize(2);
    }

    @Test
    void oneFailingQueryIsAWarning() {
        stub(query(EUROPE), serverError());
        stub(query(JAVA), okJson(Fixtures.text("jobicy/tag-java.json")));

        CollectResult result = collector(List.of(EUROPE, JAVA), 3).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(5);
        assertThat(result.warnings()).containsExactly(
                "query geo=europe&industry=engineering page 1: HTTP 500 Internal Server Error");
    }

    @Test
    void allQueriesFailingIsAFailedRun() {
        stub(get(urlPathEqualTo(PATH)), serverError());

        CollectResult result = collector(List.of(EUROPE, JAVA), 3).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).startsWith("query geo=europe&industry=engineering page 1: HTTP 500");
    }

    @Test
    void malformedJobsAreWarnings() {
        ObjectNode page = Fixtures.json("jobicy/tag-java.json").deepCopy();
        ArrayNode jobs = (ArrayNode) page.get("jobs");
        ((ObjectNode) jobs.get(0)).remove("url");
        ((ObjectNode) jobs.get(1)).put("pubDate", "yesterday");
        ((ObjectNode) jobs.get(2)).put("id", "not-a-number");
        stub(query(JAVA), okJson(page.toString()));

        CollectResult result = collector(List.of(JAVA), 3).collect(request(null, 200));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(2);
        assertThat(result.warnings()).hasSize(3)
                .anySatisfy(w -> assertThat(w).contains("item 155565").endsWith("missing url"))
                .anySatisfy(w -> assertThat(w).contains("unreadable pubDate"));
    }

    @Test
    void defaultQueriesUseVerifiedSlugs() {
        assertThat(new JobicyProperties("https://jobicy.com", null, 100, Duration.ofSeconds(2), 3).queries())
                .extracting(JobicyProperties.Query::toString)
                .containsExactly("geo=europe&industry=engineering", "geo=anywhere&industry=engineering", "tag=java");
    }

    private JobicyCollector collector(List<JobicyProperties.Query> queries, int maxPagesPerQuery) {
        var http = new HttpClients(RestClient.builder(), new HttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(5)), "");
        var properties = new JobicyProperties(wm.baseUrl(), queries, 100, Duration.ofSeconds(2), maxPagesPerQuery);
        return new JobicyCollector(http, new Politeness(pauses::add), Fixtures.JSON, properties);
    }

    private static CollectRequest request(Instant since, int maxItems) {
        return new CollectRequest(since, List.of(), maxItems);
    }

    /** Real captures: europe+engineering (5 jobs) and tag=java (5 jobs), 2 shared ids, both last pages. */
    private static void stubQueries() {
        stub(query(EUROPE), okJson(Fixtures.text("jobicy/europe-engineering.json")));
        stub(query(JAVA), okJson(Fixtures.text("jobicy/tag-java.json")));
    }

    private static MappingBuilder query(JobicyProperties.Query query) {
        MappingBuilder request = get(urlPathEqualTo(PATH));
        return query.tag() != null ? request.withQueryParam("tag", equalTo(query.tag()))
                : request.withQueryParam("geo", equalTo(query.geo()));
    }

    private static void stub(MappingBuilder request, ResponseDefinitionBuilder response) {
        wm.stubFor(request.willReturn(response));
    }
}
