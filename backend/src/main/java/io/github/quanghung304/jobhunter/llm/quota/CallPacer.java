package io.github.quanghung304.jobhunter.llm.quota;

import io.github.quanghung304.jobhunter.common.Sleeper;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Client-side pacing so a model's known {@code rpm} is not exceeded: keeps the send times of
 * the last minute per model and waits for the oldest to leave the window when it is full.
 */
@Component
public class CallPacer {

    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Sleeper sleeper;
    private final Map<String, Deque<Instant>> sent = new HashMap<>();

    public CallPacer(Clock clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** Blocks until one more request fits into the model's per-minute limit, then counts it. */
    public synchronized void acquire(RegisteredModel model) throws InterruptedException {
        Integer rpm = model.limits().rpm();
        if (rpm == null) {
            return;
        }
        Deque<Instant> times = sent.computeIfAbsent(model.id(), id -> new ArrayDeque<>());
        Instant now = clock.instant();
        purge(times, now);
        if (times.size() >= rpm) {
            Duration wait = Duration.between(now, times.peekFirst().plus(WINDOW));
            if (wait.isPositive()) {
                sleeper.sleep(wait);
            }
            now = clock.instant();
            purge(times, now);
            while (times.size() >= rpm) {
                times.pollFirst();
            }
        }
        times.addLast(now);
    }

    private static void purge(Deque<Instant> times, Instant now) {
        Instant windowStart = now.minus(WINDOW);
        while (!times.isEmpty() && !times.peekFirst().isAfter(windowStart)) {
            times.pollFirst();
        }
    }
}
