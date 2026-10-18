package io.github.quanghung304.jobhunter.llm.quota;

import io.github.quanghung304.jobhunter.llm.ModelStatus;
import java.time.Instant;

/** Current status of one registry model; {@code until} and {@code detail} may be null. */
public record ModelState(String modelId, ModelStatus status, Instant until, String detail) {
}
