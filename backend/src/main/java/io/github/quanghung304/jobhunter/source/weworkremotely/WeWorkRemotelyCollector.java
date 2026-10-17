package io.github.quanghung304.jobhunter.source.weworkremotely;

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
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads the configured WWR category RSS feeds (docs/sources/weworkremotely.md). Feeds have no
 * pagination; items older than {@code since} are skipped, the same guid in several feeds is
 * returned once, and a title without "Company: " is still returned with a warning. A failing
 * feed is a warning (PARTIAL); the run fails only if every feed failed.
 */
@Component
public class WeWorkRemotelyCollector implements JobCollector {

    private static final MediaType RSS = MediaType.parseMediaType("application/rss+xml");

    private final RestClient client;
    private final Politeness politeness;
    private final WeWorkRemotelyProperties properties;

    public WeWorkRemotelyCollector(HttpClients httpClients, Politeness politeness, WeWorkRemotelyProperties properties) {
        this.client = httpClients.forBaseUrl(properties.baseUrl());
        this.politeness = politeness;
        this.properties = properties;
    }

    @Override
    public String sourceCode() {
        return WeWorkRemotelyMapper.SOURCE_CODE;
    }

    @Override
    public CollectResult collect(CollectRequest request) {
        List<NormalizedPosting> postings = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int fetched = 0;
        int okFeeds = 0;
        String firstError = null;

        for (int i = 0; i < properties.feeds().size(); i++) {
            String feed = properties.feeds().get(i);
            List<WeWorkRemotelyItemDto> items;
            try {
                if (i > 0) {
                    politeness.pause(properties.feedDelay());
                }
                String xml = client.get().uri(feed).accept(RSS, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
                        .retrieve().body(String.class);
                items = WeWorkRemotelyFeedParser.parse(xml == null ? "" : xml);
                okFeeds++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CollectResult.failed(fetched, "interrupted before feed " + feed);
            } catch (RestClientException | IllegalArgumentException | IllegalStateException e) {
                String message = "feed " + feed + ": "
                        + (e instanceof RestClientException rce ? HttpErrors.describe(rce) : e.getMessage());
                warnings.add(message);
                firstError = firstError == null ? message : firstError;
                continue;
            }

            for (WeWorkRemotelyItemDto item : items) {
                fetched++;
                if (request.since() != null && item.pubDate() != null && item.pubDate().isBefore(request.since())) {
                    continue;
                }
                WeWorkRemotelyMapper.Mapped mapped;
                try {
                    mapped = WeWorkRemotelyMapper.toPosting(item);
                } catch (IllegalArgumentException e) {
                    warnings.add("feed " + feed + " item " + label(item) + ": " + e.getMessage());
                    continue;
                }
                if (!seen.add(mapped.posting().externalId())) {
                    continue; // same job listed in an earlier feed
                }
                if (mapped.warning() != null) {
                    warnings.add("feed " + feed + " item " + label(item) + ": " + mapped.warning());
                }
                postings.add(mapped.posting());
                if (postings.size() >= request.maxItems()) {
                    return CollectResult.completed(postings, fetched, warnings);
                }
            }
        }
        if (okFeeds == 0 && firstError != null) {
            return CollectResult.failed(fetched, firstError);
        }
        return CollectResult.completed(postings, fetched, warnings);
    }

    private static String label(WeWorkRemotelyItemDto item) {
        return item.guid() != null ? item.guid() : item.link() != null ? item.link() : "(no guid)";
    }
}
