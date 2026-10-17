package io.github.quanghung304.jobhunter.source.himalayas;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.quanghung304.jobhunter.source.CollectRequest;
import io.github.quanghung304.jobhunter.source.CollectResult;
import io.github.quanghung304.jobhunter.source.HttpClients;
import io.github.quanghung304.jobhunter.source.HttpErrors;
import io.github.quanghung304.jobhunter.source.JobCollector;
import io.github.quanghung304.jobhunter.source.NormalizedPosting;
import io.github.quanghung304.jobhunter.source.Politeness;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Walks the Himalayas browse feed newest first with cursor pagination
 * (docs/sources/himalayas.md). Stops when a response has no {@code nextCursor}, on an empty
 * page, at {@code maxItems} postings, at {@code max-pages}, or when a whole page was published
 * before {@code since} (older items inside a page are skipped).
 */
@Component
public class HimalayasCollector implements JobCollector {

    static final String FIRST_PAGE = "/jobs/api?limit={limit}";
    static final String NEXT_PAGE = "/jobs/api?limit={limit}&cursor={cursor}";

    private static final Logger log = LoggerFactory.getLogger(HimalayasCollector.class);

    private final RestClient client;
    private final Politeness politeness;
    private final ObjectMapper json;
    private final HimalayasProperties properties;

    public HimalayasCollector(HttpClients httpClients, Politeness politeness, ObjectMapper json,
            HimalayasProperties properties) {
        RestClient base = httpClients.forBaseUrl(properties.baseUrl());
        this.client = properties.hasApiKey()
                ? base.mutate().defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey().strip()).build()
                : base;
        this.politeness = politeness;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public String sourceCode() {
        return HimalayasMapper.SOURCE_CODE;
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        List<NormalizedPosting> postings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int fetched = 0;
        String cursor = null;

        for (int page = 1; page <= properties.maxPages(); page++) {
            HimalayasPageDto body;
            try {
                if (page > 1) {
                    politeness.pause(properties.pageDelay());
                }
                body = fetch(cursor);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CollectResult.failed(fetched, "interrupted on page " + page);
            } catch (RestClientException e) {
                if (page == 1) {
                    return CollectResult.failed(0, "page 1: " + HttpErrors.describe(e));
                }
                warnings.add("page " + page + " failed, kept earlier pages: " + HttpErrors.describe(e));
                break;
            }
            if (body == null || body.jobs().isEmpty()) {
                break;
            }

            boolean anyRecent = false;
            for (JsonNode raw : body.jobs()) {
                fetched++;
                HimalayasJobDto job;
                try {
                    job = json.treeToValue(raw, HimalayasJobDto.class);
                } catch (JsonProcessingException e) {
                    warnings.add(itemLabel(raw, page) + ": unreadable item: " + e.getOriginalMessage());
                    continue;
                }
                if (isOlderThan(job, request.since())) {
                    continue;
                }
                anyRecent = true;
                if (job.guid() != null && !seen.add(job.guid())) {
                    continue;
                }
                try {
                    postings.add(HimalayasMapper.toPosting(job, raw));
                } catch (IllegalArgumentException e) {
                    warnings.add(itemLabel(raw, page) + ": " + e.getMessage());
                    continue;
                }
                if (postings.size() >= request.maxItems()) {
                    return CollectResult.completed(postings, fetched, warnings);
                }
            }
            if (request.since() != null && !anyRecent) {
                log.debug("himalayas: page {} is entirely older than {}, stopping", page, request.since());
                break;
            }
            if (!body.hasNext()) {
                break;
            }
            cursor = body.nextCursor();
        }
        return CollectResult.completed(postings, fetched, warnings);
    }

    private HimalayasPageDto fetch(String cursor) {
        // The cursor is passed as a URI variable so it is strictly encoded (it is base64).
        RestClient.RequestHeadersSpec<?> spec = cursor == null
                ? client.get().uri(FIRST_PAGE, properties.pageSize())
                : client.get().uri(NEXT_PAGE, properties.pageSize(), cursor);
        return spec.retrieve().body(HimalayasPageDto.class);
    }

    private static boolean isOlderThan(HimalayasJobDto job, Instant since) {
        return since != null && job.pubDate() != null && Instant.ofEpochSecond(job.pubDate()).isBefore(since);
    }

    private static String itemLabel(JsonNode raw, int page) {
        JsonNode guid = raw.get("guid");
        return "page " + page + " item " + (guid != null && guid.isTextual() ? guid.asText() : "(no guid)");
    }
}
