package io.github.quanghung304.jobhunter.ingest;

import java.time.Instant;

/** One source's listing of a job ({@code raw_posting}): the attribution link the UI shows. */
public record PostingView(String sourceCode, String url, Instant firstSeenAt, Instant lastSeenAt) {
}
