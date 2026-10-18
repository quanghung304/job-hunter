package io.github.quanghung304.jobhunter.common;

import java.time.Duration;

/** Abstraction over {@link Thread#sleep} so tests can record pauses instead of waiting. */
@FunctionalInterface
public interface Sleeper {

    Sleeper SYSTEM = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
