package io.github.quanghung304.jobhunter.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Progress of long-running work for the admin page ({@code pipelineProgress} in api-spec): the
 * pipeline run and the admin bulk LLM actions publish their stage and counters here. When
 * several activities run at once, the one started last is shown.
 */
@Component
public class ProgressBoard {

    /** What is running: pipeline steps, or the owner's bulk LLM actions (ADR-0010). */
    public enum Stage {
        COLLECT,
        PREFILTER,
        EXPIRE,
        ANALYZE,
        SCORE
    }

    /** One published state; {@code percent} is 0–100. */
    public record Progress(Stage stage, String text, int percent) {

        public Progress {
            percent = Math.max(0, Math.min(100, percent));
        }
    }

    private final Deque<Activity> open = new ArrayDeque<>();

    /** Starts an activity; close it when the work ends (try-with-resources). */
    public synchronized Activity begin(Stage stage, String text) {
        Activity activity = new Activity(new Progress(stage, text, 0));
        open.addLast(activity);
        return activity;
    }

    /** The newest open activity's progress. */
    public synchronized Optional<Progress> current() {
        return Optional.ofNullable(open.peekLast()).map(activity -> activity.progress);
    }

    private synchronized void update(Activity activity, Progress progress) {
        activity.progress = progress;
    }

    private synchronized void close(Activity activity) {
        open.remove(activity);
    }

    /** A handle to update or end one activity's progress. */
    public final class Activity implements AutoCloseable {

        private Progress progress;

        private Activity(Progress progress) {
            this.progress = progress;
        }

        public void update(Stage stage, String text, int percent) {
            ProgressBoard.this.update(this, new Progress(stage, text, percent));
        }

        /** {@code done} of {@code total} items in the current stage. */
        public void update(Stage stage, String text, int done, int total) {
            update(stage, text, total == 0 ? 100 : done * 100 / total);
        }

        @Override
        public void close() {
            ProgressBoard.this.close(this);
        }
    }
}
