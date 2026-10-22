package io.github.quanghung304.jobhunter.source.hn;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the monthly "Ask HN: Who is hiring?" thread through the Algolia HN Search API
 * (docs/sources/hn-whoishiring.md): one search for the threads, then one fetch of the latest
 * thread, plus the previous month's thread during the first days of a month. Each top-level
 * comment is one posting; nested replies are ignored. Comments older than {@code since} are
 * skipped (a thread is not ordered by time). A failing thread fetch is a warning (PARTIAL);
 * the run fails if the search or every thread fetch failed.
 */
@Component
public class HnCollector implements JobCollector {

    static final String SEARCH_PATH = "/api/v1/search_by_date";
    static final String ITEM_PATH = "/api/v1/items/{id}";
    static final String SEARCH_TAGS = "story,author_whoishiring";
    static final String THREAD_TITLE_PREFIX = "Ask HN: Who is hiring?";

    private static final Logger log = LoggerFactory.getLogger(HnCollector.class);

    private final RestClient client;
    private final Politeness politeness;
    private final ObjectMapper json;
    private final HnProperties properties;
    private final Clock clock;

    public HnCollector(HttpClients httpClients, Politeness politeness, ObjectMapper json, HnProperties properties,
            Clock clock) {
        this.client = httpClients.forBaseUrl(properties.baseUrl());
        this.politeness = politeness;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String sourceCode() {
        return HnMapper.SOURCE_CODE;
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        List<Story> threads;
        try {
            threads = threadsToFetch(search().hits(), clock.instant(), properties.previousThreadDays());
        } catch (RestClientException e) {
            return CollectResult.failed(0, "thread search: " + HttpErrors.describe(e));
        }
        if (threads.isEmpty()) {
            return CollectResult.failed(0, "thread search: no '" + THREAD_TITLE_PREFIX + "' story found");
        }
        var run = new Run(request);
        for (Story thread : threads) {
            try {
                politeness.pause(properties.callDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CollectResult.failed(run.fetched, "interrupted before thread " + thread.id());
            }
            if (!collectThread(thread, run)) {
                break; // max-items reached
            }
        }
        if (run.successfulThreads == 0) {
            return CollectResult.failed(run.fetched, run.firstError);
        }
        return CollectResult.completed(run.postings, run.fetched, run.warnings);
    }

    /** @return false when max-items is reached and the whole run should stop */
    private boolean collectThread(Story thread, Run run) {
        HnItemDto item;
        try {
            item = client.get().uri(ITEM_PATH, thread.id()).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(HnItemDto.class);
            run.successfulThreads++;
        } catch (RestClientException e) {
            String message = "thread " + thread.id() + ": " + HttpErrors.describe(e);
            run.warnings.add(message);
            if (run.firstError == null) {
                run.firstError = message;
            }
            return true;
        }
        if (item == null) {
            return true;
        }
        int withoutTitle = 0;
        for (JsonNode raw : item.children()) {
            run.fetched++;
            Optional<NormalizedPosting> posting = map(raw, thread, run);
            if (posting.isEmpty()) {
                continue;
            }
            if (posting.get().title().equals(HnHeaderParser.NO_TITLE)) {
                withoutTitle++;
            }
            Instant postedAt = posting.get().postedAt();
            if (run.request.since() != null && postedAt != null && postedAt.isBefore(run.request.since())) {
                continue;
            }
            if (!run.seenIds.add(posting.get().externalId())) {
                continue;
            }
            run.postings.add(posting.get());
            if (run.postings.size() >= run.request.maxItems()) {
                return false;
            }
        }
        log.debug("source={} thread={} children={} withoutTitle={}", sourceCode(), thread.id(),
                item.children().size(), withoutTitle);
        return true;
    }

    private Optional<NormalizedPosting> map(JsonNode raw, Story thread, Run run) {
        try {
            HnCommentDto comment = json.treeToValue(raw, HnCommentDto.class);
            return HnMapper.isPost(comment) ? Optional.of(HnMapper.toPosting(comment, raw)) : Optional.empty();
        } catch (JsonProcessingException e) {
            run.warnings.add(itemLabel(raw, thread) + ": unreadable item: " + e.getOriginalMessage());
        } catch (IllegalArgumentException e) {
            run.warnings.add(itemLabel(raw, thread) + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private HnSearchDto search() {
        HnSearchDto body = client.get()
                .uri(builder -> builder.path(SEARCH_PATH)
                        .queryParam("tags", SEARCH_TAGS)
                        .queryParam("hitsPerPage", properties.searchHits())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(HnSearchDto.class);
        return body == null ? new HnSearchDto(List.of()) : body;
    }

    /**
     * The newest "Who is hiring?" story, plus the one before it when today (UTC) is within the
     * first {@code previousThreadDays} days of the newest story's month.
     */
    static List<Story> threadsToFetch(List<HnSearchDto.Hit> hits, Instant now, int previousThreadDays) {
        List<Story> threads = hits.stream()
                .filter(h -> h.title() != null && h.title().startsWith(THREAD_TITLE_PREFIX))
                .filter(h -> h.objectId() != null && !h.objectId().isBlank())
                .map(h -> new Story(h.objectId().strip(), h.title(), createdAt(h)))
                .sorted(Comparator.comparing(Story::createdAt).reversed())
                .toList();
        if (threads.isEmpty()) {
            return List.of();
        }
        Story latest = threads.get(0);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        boolean latestIsThisMonth = YearMonth.from(today).equals(YearMonth.from(latest.createdAt().atZone(ZoneOffset.UTC)));
        if (latestIsThisMonth && today.getDayOfMonth() <= previousThreadDays && threads.size() > 1) {
            return threads.subList(0, 2);
        }
        return List.of(latest);
    }

    private static Instant createdAt(HnSearchDto.Hit hit) {
        if (hit.createdAt() != null) {
            try {
                return Instant.parse(hit.createdAt().strip());
            } catch (DateTimeParseException ignored) {
                // fall back to the epoch seconds
            }
        }
        return hit.createdAtEpoch() == null ? Instant.EPOCH : Instant.ofEpochSecond(hit.createdAtEpoch());
    }

    private static String itemLabel(JsonNode raw, Story thread) {
        JsonNode id = raw.get("id");
        return "thread " + thread.id() + " item " + (id != null && !id.isNull() ? id.asText() : "(no id)");
    }

    /** One "Who is hiring?" story. */
    record Story(String id, String title, Instant createdAt) {
    }

    /** Mutable state of one collect() call. */
    private static final class Run {
        final CollectRequest request;
        final List<NormalizedPosting> postings = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final Set<String> seenIds = new HashSet<>();
        int fetched;
        int successfulThreads;
        String firstError;

        Run(CollectRequest request) {
            this.request = request;
        }
    }
}
