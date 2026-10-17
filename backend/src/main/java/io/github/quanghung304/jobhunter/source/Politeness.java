package io.github.quanghung304.jobhunter.source;

import java.time.Duration;
import org.springframework.stereotype.Component;

/** Waits between requests to the same source, per its rate rule (docs/sources/&lt;code&gt;.md). */
@Component
public class Politeness {

    private final Sleeper sleeper;

    public Politeness(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /** @throws InterruptedException if the run was cancelled (e.g. collector timeout) */
    public void pause(Duration delay) throws InterruptedException {
        if (!delay.isZero() && !delay.isNegative()) {
            sleeper.sleep(delay);
        }
    }
}
