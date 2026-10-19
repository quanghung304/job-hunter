package io.github.quanghung304.jobhunter.common;

/** Receives "done of total" updates from a long-running loop (bulk LLM actions). */
@FunctionalInterface
public interface ProgressListener {

    ProgressListener NONE = (done, total) -> {
    };

    void progress(int done, int total);
}
