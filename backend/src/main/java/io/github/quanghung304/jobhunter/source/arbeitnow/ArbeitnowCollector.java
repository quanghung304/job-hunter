package io.github.quanghung304.jobhunter.source.arbeitnow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.JobCollector;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.Politeness;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pages through the Arbeitnow job board API (docs/sources/arbeitnow.md).
 *
 * <p>Items are not strictly ordered by {@code created_at}, so an item older than
 * {@code since} is skipped, and paging stops only when a whole page is older. Paging also
 * stops on an empty page or {@code links.next == null}, at {@code maxItems} postings and at
 * {@code max-pages}. The page number is incremented locally; {@code links.next} is only used
 * as the "more pages" flag (it is an absolute URL to the production host).
 */
@Component
public class ArbeitnowCollector implements JobCollector {

    static final String PATH = "/api/job-board-api?page={page}";

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowCollector.class);

    private final RestClient client;
    private final Politeness politeness;
    private final ObjectMapper json;
    private final ArbeitnowProperties properties;

    public ArbeitnowCollector(HttpClients httpClients, Politeness politeness, ObjectMapper json,
            ArbeitnowProperties properties) {
        this.client = httpClients.forBaseUrl(properties.baseUrl());
        this.politeness = politeness;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public String sourceCode() {
        return ArbeitnowMapper.SOURCE_CODE;
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        List<NormalizedPosting> postings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int fetched = 0;

        for (int page = 1; page <= properties.maxPages(); page++) {
            ArbeitnowPageDto body;
            try {
                if (page > 1) {
                    politeness.pause(properties.pageDelay());
                }
                body = client.get().uri(PATH, page).retrieve().body(ArbeitnowPageDto.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CollectResult.failed(fetched, "interrupted on page " + page);
            } catch (RestClientException e) {
                if (page == 1) {
                    return CollectResult.failed(0, "page 1: " + e.getMessage());
                }
                warnings.add("page " + page + " failed, kept earlier pages: " + e.getMessage());
                break;
            }
            if (body == null || body.data().isEmpty()) {
                break;
            }

            boolean anyRecent = false;
            for (JsonNode raw : body.data()) {
                fetched++;
                ArbeitnowJobDto item;
                try {
                    item = json.treeToValue(raw, ArbeitnowJobDto.class);
                } catch (JsonProcessingException e) {
                    warnings.add(itemLabel(raw, page) + ": unreadable item: " + e.getOriginalMessage());
                    continue;
                }
                if (isOlderThan(item, request.since())) {
                    continue;
                }
                anyRecent = true;
                if (properties.remoteOnly() && !ArbeitnowMapper.isRemote(item)) {
                    continue;
                }
                try {
                    postings.add(ArbeitnowMapper.toPosting(item, raw));
                } catch (IllegalArgumentException e) {
                    warnings.add(itemLabel(raw, page) + ": " + e.getMessage());
                    continue;
                }
                if (postings.size() >= request.maxItems()) {
                    return CollectResult.completed(postings, fetched, warnings);
                }
            }
            if (request.since() != null && !anyRecent) {
                log.debug("arbeitnow: page {} is entirely older than {}, stopping", page, request.since());
                break;
            }
            if (!body.hasNext()) {
                break;
            }
        }
        return CollectResult.completed(postings, fetched, warnings);
    }

    private static boolean isOlderThan(ArbeitnowJobDto item, Instant since) {
        return since != null && item.createdAt() != null && Instant.ofEpochSecond(item.createdAt()).isBefore(since);
    }

    private static String itemLabel(JsonNode raw, int page) {
        JsonNode slug = raw.get("slug");
        return "page " + page + " item " + (slug != null && slug.isTextual() ? slug.asText() : "(no slug)");
    }
}
