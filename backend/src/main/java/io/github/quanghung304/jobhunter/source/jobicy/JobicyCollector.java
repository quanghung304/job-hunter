package io.github.quanghung304.jobhunter.source.jobicy;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Runs each configured query against the Jobicy feed (docs/sources/jobicy.md). The feed is
 * ordered by publication time, newest first: a query stops at its first job older than
 * {@code since}, at {@code nextCursor: null}, or after {@code max-pages-per-query}. Jobs are
 * deduplicated by id across queries. One failing query is a warning (PARTIAL); the run fails
 * only if every request failed.
 */
@Component
public class JobicyCollector implements JobCollector {

    static final String PATH = "/api/v2/remote-jobs";

    private final RestClient client;
    private final Politeness politeness;
    private final ObjectMapper json;
    private final JobicyProperties properties;

    public JobicyCollector(HttpClients httpClients, Politeness politeness, ObjectMapper json,
            JobicyProperties properties) {
        this.client = httpClients.forBaseUrl(properties.baseUrl());
        this.politeness = politeness;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public String sourceCode() {
        return JobicyMapper.SOURCE_CODE;
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        var run = new Run(request);
        for (JobicyProperties.Query query : properties.queries()) {
            try {
                if (!collectQuery(query, run)) {
                    break; // max-items reached
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CollectResult.failed(run.fetched, "interrupted during query " + query);
            }
        }
        if (run.successfulCalls == 0 && run.firstError != null) {
            return CollectResult.failed(run.fetched, run.firstError);
        }
        return CollectResult.completed(run.postings, run.fetched, run.warnings);
    }

    /** @return false when max-items is reached and the whole run should stop */
    private boolean collectQuery(JobicyProperties.Query query, Run run) throws InterruptedException {
        String cursor = null;
        for (int page = 1; page <= properties.maxPagesPerQuery(); page++) {
            if (run.calls++ > 0) {
                politeness.pause(properties.callDelay());
            }
            JobicyPageDto body;
            try {
                body = fetch(query, cursor);
                run.successfulCalls++;
            } catch (RestClientException e) {
                String message = "query " + query + " page " + page + ": " + HttpErrors.describe(e);
                run.warnings.add(message);
                if (run.firstError == null) {
                    run.firstError = message;
                }
                return true;
            }
            if (body == null || body.jobs().isEmpty()) {
                return true;
            }
            for (JsonNode raw : body.jobs()) {
                run.fetched++;
                Optional<NormalizedPosting> posting = map(raw, query, page, run);
                if (posting.isEmpty()) {
                    continue;
                }
                if (run.request.since() != null && posting.get().postedAt() != null
                        && posting.get().postedAt().isBefore(run.request.since())) {
                    return true; // newest first: the rest of this query is older too
                }
                if (!run.seenIds.add(posting.get().externalId())) {
                    continue; // already returned by an earlier query
                }
                run.postings.add(posting.get());
                if (run.postings.size() >= run.request.maxItems()) {
                    return false;
                }
            }
            if (!body.hasNext()) {
                return true;
            }
            cursor = body.nextCursor();
        }
        return true;
    }

    private Optional<NormalizedPosting> map(JsonNode raw, JobicyProperties.Query query, int page, Run run) {
        try {
            return Optional.of(JobicyMapper.toPosting(json.treeToValue(raw, JobicyJobDto.class), raw));
        } catch (JsonProcessingException e) {
            run.warnings.add(itemLabel(raw, query, page) + ": unreadable item: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            run.warnings.add(itemLabel(raw, query, page) + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private JobicyPageDto fetch(JobicyProperties.Query query, String cursor) {
        return client.get()
                .uri(builder -> builder.path(PATH)
                        .queryParam("count", properties.count())
                        .queryParamIfPresent("geo", nonBlank(query.geo()))
                        .queryParamIfPresent("industry", nonBlank(query.industry()))
                        .queryParamIfPresent("tag", nonBlank(query.tag()))
                        .queryParamIfPresent("cursor", nonBlank(cursor))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JobicyPageDto.class);
    }

    private static Optional<String> nonBlank(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    private static String itemLabel(JsonNode raw, JobicyProperties.Query query, int page) {
        JsonNode id = raw.get("id");
        return "query " + query + " page " + page + " item " + (id != null && !id.isNull() ? id.asText() : "(no id)");
    }

    /** Mutable state of one collect() call. */
    private static final class Run {
        final CollectRequest request;
        final List<NormalizedPosting> postings = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final Set<String> seenIds = new HashSet<>();
        int fetched;
        int calls;
        int successfulCalls;
        String firstError;

        Run(CollectRequest request) {
            this.request = request;
        }
    }
}
