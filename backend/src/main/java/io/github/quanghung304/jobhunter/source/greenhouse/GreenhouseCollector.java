package io.github.quanghung304.jobhunter.source.greenhouse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.source.AtsType;
import io.github.quanghung304.jobhunter.source.CompanyBoard;
import io.github.quanghung304.jobhunter.source.CompanyBoardCollector;
import io.github.quanghung304.jobhunter.source.CompanyBoards;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.Politeness;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Greenhouse Job Board API: {@code GET /v1/boards/{slug}/jobs?content=true} per company (docs/sources/ats.md). */
@Component
public class GreenhouseCollector extends CompanyBoardCollector {

    static final String PATH = "/v1/boards/{slug}/jobs";

    private final RestClient client;
    private final ObjectMapper json;

    public GreenhouseCollector(HttpClients httpClients, Politeness politeness, ObjectMapper json,
            CompanyBoards boards, GreenhouseProperties properties) {
        super(boards, politeness, properties.requestDelay());
        this.client = httpClients.forBaseUrl(properties.baseUrl());
        this.json = json;
    }

    @Override
    public String sourceCode() {
        return GreenhouseMapper.SOURCE_CODE;
    }

    @Override
    public Set<AtsType> atsTypes() {
        return Set.of(AtsType.GREENHOUSE);
    }

    @Override
    protected List<JsonNode> fetchBoard(CompanyBoard board) {
        BoardDto body = client.get()
                .uri(builder -> builder.path(PATH).queryParam("content", true).build(board.slug()))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BoardDto.class);
        return body == null || body.jobs() == null ? List.of() : body.jobs();
    }

    @Override
    protected Optional<NormalizedPosting> map(CompanyBoard board, JsonNode raw) throws JsonProcessingException {
        return Optional.of(GreenhouseMapper.toPosting(board, json.treeToValue(raw, GreenhouseJobDto.class), raw));
    }

    /** The board response; jobs stay raw JSON (rawPayload; a malformed job does not fail the board). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record BoardDto(List<JsonNode> jobs) {
    }
}
