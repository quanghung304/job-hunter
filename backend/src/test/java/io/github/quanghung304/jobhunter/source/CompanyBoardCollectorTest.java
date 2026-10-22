package io.github.quanghung304.jobhunter.source;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.quanghung304.jobhunter.Fixtures;
import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.source.ashby.AshbyCollector;
import io.github.quanghung304.jobhunter.source.ashby.AshbyProperties;
import io.github.quanghung304.jobhunter.source.greenhouse.GreenhouseCollector;
import io.github.quanghung304.jobhunter.source.greenhouse.GreenhouseProperties;
import io.github.quanghung304.jobhunter.source.lever.LeverCollector;
import io.github.quanghung304.jobhunter.source.lever.LeverProperties;
import io.github.quanghung304.jobhunter.source.persistence.Company;
import io.github.quanghung304.jobhunter.source.persistence.CompanyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

/** The shared board loop, tested through the real collectors against WireMock and captured boards. */
class CompanyBoardCollectorTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final List<Company> companies = new ArrayList<>();
    private final List<Duration> pauses = new ArrayList<>();
    private final CompanyBoards boards = new CompanyBoards(repository(), new MutableClock(NOW));

    @Test
    void readsEveryEnabledBoardAndRecordsOk() {
        Company gitlab = company(1, "GitLab", AtsType.GREENHOUSE, "gitlab");
        Company acme = company(2, "Acme", AtsType.GREENHOUSE, "acme");
        company(3, "Off", AtsType.GREENHOUSE, "off").setEnabled(false);
        company(4, "Linear", AtsType.ASHBY, "linear");
        stub("/v1/boards/gitlab/jobs", okJson(Fixtures.text("greenhouse/gitlab.json")));
        stub("/v1/boards/acme/jobs", okJson("{\"jobs\": [], \"meta\": {\"total\": 0}}"));

        CollectResult result = greenhouse().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(5);
        assertThat(result.postings()).hasSize(5).allMatch(p -> p.externalId().startsWith("gitlab:"));
        assertThat(pauses).containsExactly(Duration.ofSeconds(1));
        assertThat(gitlab.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.OK);
        assertThat(gitlab.getLastCheckedAt()).isEqualTo(NOW);
        assertThat(acme.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.OK);
        wm.verify(getRequestedFor(urlPathEqualTo("/v1/boards/gitlab/jobs")).withQueryParam("content", equalTo("true")));
        wm.verify(0, getRequestedFor(urlPathEqualTo("/v1/boards/off/jobs")));
    }

    @Test
    void aMissingBoardIsAWarningAndThreeInARowDisableIt() {
        Company gone = company(1, "Gone", AtsType.GREENHOUSE, "gone");
        company(2, "GitLab", AtsType.GREENHOUSE, "gitlab");
        stub("/v1/boards/gone/jobs", notFound().withBody("{\"status\":404,\"error\":\"Job not found\"}"));
        stub("/v1/boards/gitlab/jobs", okJson(Fixtures.text("greenhouse/gitlab.json")));
        GreenhouseCollector collector = greenhouse();

        CollectResult first = collector.collect(request(500));

        assertThat(first.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(first.postings()).hasSize(5);
        assertThat(first.warnings()).containsExactly("Gone (GREENHOUSE gone): board not found (404, 1 in a row)");
        assertThat(gone.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.NOT_FOUND);
        assertThat(gone.isEnabled()).isTrue();

        collector.collect(request(500));
        CollectResult third = collector.collect(request(500));

        assertThat(third.warnings()).containsExactly("Gone (GREENHOUSE gone): board not found (404, 3 in a row, disabled)");
        assertThat(gone.isEnabled()).isFalse();
        assertThat(gone.getNotFoundCount()).isEqualTo(3);

        CollectResult fourth = collector.collect(request(500));

        assertThat(fourth.status()).isEqualTo(CollectStatus.SUCCESS);
        wm.verify(3, getRequestedFor(urlPathEqualTo("/v1/boards/gone/jobs")));
    }

    @Test
    void onlyMissingBoardsIsPartialNotFailed() {
        company(1, "Gone", AtsType.ASHBY, "gone");
        stub("/posting-api/job-board/gone", notFound().withBody("Not Found"));

        CollectResult result = ashby().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).isEmpty();
    }

    @Test
    void aServerErrorOnOneBoardIsAWarning() {
        Company broken = company(1, "Broken", AtsType.GREENHOUSE, "broken");
        company(2, "GitLab", AtsType.GREENHOUSE, "gitlab");
        stub("/v1/boards/broken/jobs", serverError());
        stub("/v1/boards/gitlab/jobs", okJson(Fixtures.text("greenhouse/gitlab.json")));

        CollectResult result = greenhouse().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.warnings()).containsExactly("Broken (GREENHOUSE broken): HTTP 500 Internal Server Error");
        assertThat(broken.getLastCheckStatus()).isEqualTo(CompanyCheckStatus.ERROR);
        assertThat(broken.getNotFoundCount()).isZero();
    }

    @Test
    void serverErrorsOnEveryBoardFailTheRun() {
        company(1, "Broken", AtsType.GREENHOUSE, "broken");
        stub("/v1/boards/broken/jobs", serverError());

        CollectResult result = greenhouse().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("Broken (GREENHOUSE broken): HTTP 500 Internal Server Error");
    }

    @Test
    void noBoardsIsAnEmptySuccess() {
        CollectResult result = lever().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.postings()).isEmpty();
    }

    @Test
    void leverEuBoardsUseTheEuHost() {
        company(1, "Spotify", AtsType.LEVER, "spotify");
        company(2, "Acme EU", AtsType.LEVER_EU, "acme");
        stub("/v0/postings/spotify", okJson(Fixtures.text("lever/spotify.json")));
        stub("/eu/v0/postings/acme", okJson("[]"));

        CollectResult result = lever().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.postings()).hasSize(5);
        wm.verify(getRequestedFor(urlPathEqualTo("/v0/postings/spotify")).withQueryParam("mode", equalTo("json")));
        wm.verify(getRequestedFor(urlPathEqualTo("/eu/v0/postings/acme")).withQueryParam("mode", equalTo("json")));
    }

    @Test
    void ashbyAsksForCompensationAndSkipsUnlistedJobs() {
        company(1, "Linear", AtsType.ASHBY, "linear");
        ObjectNode board = Fixtures.json("ashby/linear.json").deepCopy();
        ((ObjectNode) board.get("jobs").get(1)).put("isListed", false);
        stub("/posting-api/job-board/linear", okJson(board.toString()));

        CollectResult result = ashby().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.postings()).extracting(NormalizedPosting::externalId)
                .containsExactly("linear:d3bc1ced-3ce4-4086-a050-555055dbb1ff", "linear:f16dd5e9-ed10-4b8e-b5d7-1b8b5e3f002f");
        wm.verify(getRequestedFor(urlPathEqualTo("/posting-api/job-board/linear"))
                .withQueryParam("includeCompensation", equalTo("true")));
    }

    @Test
    void malformedJobsAreWarnings() {
        company(1, "GitLab", AtsType.GREENHOUSE, "gitlab");
        ObjectNode board = Fixtures.json("greenhouse/gitlab.json").deepCopy();
        ArrayNode jobs = (ArrayNode) board.get("jobs");
        ((ObjectNode) jobs.get(0)).remove("absolute_url");
        ((ObjectNode) jobs.get(1)).put("id", "not-a-number");
        stub("/v1/boards/gitlab/jobs", okJson(board.toString()));

        CollectResult result = greenhouse().collect(request(500));

        assertThat(result.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(result.postings()).hasSize(3);
        assertThat(result.warnings()).hasSize(2)
                .anySatisfy(w -> assertThat(w).isEqualTo("GitLab (GREENHOUSE gitlab) item 8790102002: missing absolute_url"))
                .anySatisfy(w -> assertThat(w).startsWith("GitLab (GREENHOUSE gitlab) item not-a-number: unreadable item"));
    }

    @Test
    void stopsAtMaxItems() {
        company(1, "GitLab", AtsType.GREENHOUSE, "gitlab");
        company(2, "Later", AtsType.GREENHOUSE, "later");
        stub("/v1/boards/gitlab/jobs", okJson(Fixtures.text("greenhouse/gitlab.json")));

        CollectResult result = greenhouse().collect(request(2));

        assertThat(result.postings()).hasSize(2);
        wm.verify(0, getRequestedFor(urlPathEqualTo("/v1/boards/later/jobs")));
    }

    @Test
    void checkEnablesADisabledBoardThatAnswersAgain() {
        Company back = company(1, "Back", AtsType.ASHBY, "linear");
        back.setEnabled(false);
        back.setNotFoundCount(3);
        stub("/posting-api/job-board/linear", okJson(Fixtures.text("ashby/linear.json")));

        CompanyBoardCollector.BoardCheck check = ashby().check(boards.all(Set.of(AtsType.ASHBY)).get(0));

        assertThat(check.status()).isEqualTo(CompanyCheckStatus.OK);
        assertThat(check.jobs()).isEqualTo(3);
        assertThat(back.isEnabled()).isTrue();
        assertThat(back.getNotFoundCount()).isZero();
    }

    @Test
    void checkerReportsEveryBoard() throws InterruptedException {
        company(1, "GitLab", AtsType.GREENHOUSE, "gitlab");
        company(2, "Gone", AtsType.LEVER, "gone").setEnabled(false);
        company(3, "Broken", AtsType.ASHBY, "broken");
        stub("/v1/boards/gitlab/jobs", okJson(Fixtures.text("greenhouse/gitlab.json")));
        stub("/v0/postings/gone", notFound().withBody("{\"ok\":false,\"error\":\"Document not found\"}"));
        stub("/posting-api/job-board/broken", serverError());
        var checker = new CompanyBoardChecker(List.of(greenhouse(), lever(), ashby()), boards, new Politeness(pauses::add));

        var checks = checker.checkAll();

        assertThat(checks).extracting(CompanyBoardCollector.BoardCheck::status)
                .containsExactly(CompanyCheckStatus.ERROR, CompanyCheckStatus.OK, CompanyCheckStatus.NOT_FOUND);
        assertThat(checks.get(1).jobs()).isEqualTo(5);
        assertThat(pauses).hasSize(2);
        assertThat(CompanyBoardChecker.report(checks))
                .contains("OK            5  GREENHOUSE gitlab")
                .contains("NOT_FOUND     0  LEVER      gone")
                .contains("(HTTP 500 Internal Server Error)")
                .endsWith("1 of 3 boards OK" + System.lineSeparator());
    }

    private GreenhouseCollector greenhouse() {
        return new GreenhouseCollector(http(), new Politeness(pauses::add), Fixtures.JSON, boards,
                new GreenhouseProperties(wm.baseUrl(), Duration.ofSeconds(1)));
    }

    private LeverCollector lever() {
        return new LeverCollector(http(), new Politeness(pauses::add), Fixtures.JSON, boards,
                new LeverProperties(wm.baseUrl(), wm.baseUrl() + "/eu", Duration.ofSeconds(1)));
    }

    private AshbyCollector ashby() {
        return new AshbyCollector(http(), new Politeness(pauses::add), Fixtures.JSON, boards,
                new AshbyProperties(wm.baseUrl(), Duration.ofSeconds(1)));
    }

    private static HttpClients http() {
        return new HttpClients(RestClient.builder(), new HttpProperties(Duration.ofSeconds(2), Duration.ofSeconds(5)), "");
    }

    private static CollectRequest request(int maxItems) {
        return new CollectRequest(null, List.of(), maxItems);
    }

    private static void stub(String path, ResponseDefinitionBuilder response) {
        wm.stubFor(get(urlPathEqualTo(path)).willReturn(response));
    }

    private Company company(long id, String name, AtsType ats, String slug) {
        var company = new Company(name, ats, slug);
        ReflectionTestUtils.setField(company, "id", id);
        companies.add(company);
        return company;
    }

    /** In-memory {@link CompanyRepository} over {@link #companies}, ordered by name like the real queries. */
    @SuppressWarnings("unchecked")
    private CompanyRepository repository() {
        CompanyRepository repository = mock(CompanyRepository.class);
        when(repository.findById(anyLong())).thenAnswer(inv -> companies.stream()
                .filter(c -> c.getId().equals(inv.getArgument(0))).findFirst());
        when(repository.findByAtsInOrderByNameAscIdAsc(any())).thenAnswer(inv -> companies.stream()
                .filter(c -> ((Collection<AtsType>) inv.getArgument(0)).contains(c.getAts()))
                .sorted(Comparator.comparing(Company::getName)).toList());
        when(repository.findByAtsInAndEnabledTrueOrderByNameAscIdAsc(any())).thenAnswer(inv -> companies.stream()
                .filter(c -> c.isEnabled() && ((Collection<AtsType>) inv.getArgument(0)).contains(c.getAts()))
                .sorted(Comparator.comparing(Company::getName)).toList());
        return repository;
    }
}
