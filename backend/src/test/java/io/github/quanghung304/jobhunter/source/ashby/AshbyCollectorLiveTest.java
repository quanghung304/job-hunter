package io.github.quanghung304.jobhunter.source.ashby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.common.Sleeper;
import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.CompanyBoards;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.HttpProperties;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.Politeness;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Opt-in, one real request (the Linear board): {@code ./mvnw verify -Plive}. */
@Tag("live")
class AshbyCollectorLiveTest {

    @Test
    void boardReturnsUniqueMappedPostings() throws Exception {
        var http = new HttpClients(RestClient.builder(),
                new HttpProperties(Duration.ofSeconds(5), Duration.ofSeconds(30)), System.getenv("OWNER_CONTACT_EMAIL"));
        var collector = new AshbyCollector(http, new Politeness(Sleeper.SYSTEM), new ObjectMapper(), mock(CompanyBoards.class),
                new AshbyProperties("https://api.ashbyhq.com", Duration.ofSeconds(1)));
        var board = new CompanyBoard(1, "Linear", AtsType.ASHBY, "linear");

        List<JsonNode> jobs = collector.fetchBoard(board);
        var postings = new ArrayList<NormalizedPosting>();
        for (JsonNode raw : jobs) {
            collector.map(board, raw).ifPresent(postings::add);
        }

        assertThat(postings).isNotEmpty().allSatisfy(p -> {
            assertThat(p.externalId()).startsWith("linear:");
            assertThat(p.url()).startsWith("https://");
            assertThat(p.title()).isNotBlank();
            assertThat(p.descriptionText()).isNotBlank().doesNotContain("<p>", "&lt;");
        });
        var ids = new HashSet<String>();
        assertThat(postings).allMatch(p -> ids.add(p.externalId()));
    }
}
