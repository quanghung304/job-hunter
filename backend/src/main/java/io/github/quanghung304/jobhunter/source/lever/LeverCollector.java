package io.github.quanghung304.jobhunter.source.lever;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Lever Postings API: {@code GET /v0/postings/{slug}?mode=json} per company, on the EU host for
 * {@code LEVER_EU} boards (docs/sources/ats.md).
 */
@Component
public class LeverCollector extends CompanyBoardCollector {

    static final String PATH = "/v0/postings/{slug}";

    private final RestClient client;
    private final RestClient euClient;
    private final ObjectMapper json;

    public LeverCollector(HttpClients httpClients, Politeness politeness, ObjectMapper json, CompanyBoards boards,
            LeverProperties properties) {
        super(boards, politeness, properties.requestDelay());
        this.client = httpClients.forBaseUrl(properties.baseUrl());
        this.euClient = httpClients.forBaseUrl(properties.euBaseUrl());
        this.json = json;
    }

    @Override
    public String sourceCode() {
        return LeverMapper.SOURCE_CODE;
    }

    @Override
    public Set<AtsType> atsTypes() {
        return Set.of(AtsType.LEVER, AtsType.LEVER_EU);
    }

    @Override
    protected List<JsonNode> fetchBoard(CompanyBoard board) {
        JsonNode body = (board.ats() == AtsType.LEVER_EU ? euClient : client).get()
                .uri(builder -> builder.path(PATH).queryParam("mode", "json").build(board.slug()))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        var postings = new ArrayList<JsonNode>();
        if (body != null && body.isArray()) {
            body.forEach(postings::add);
        }
        return postings;
    }

    @Override
    protected Optional<NormalizedPosting> map(CompanyBoard board, JsonNode raw) throws JsonProcessingException {
        return Optional.of(LeverMapper.toPosting(board, json.treeToValue(raw, LeverPostingDto.class), raw));
    }
}
