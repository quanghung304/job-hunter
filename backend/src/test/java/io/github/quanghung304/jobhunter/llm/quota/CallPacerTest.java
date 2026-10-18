package io.github.quanghung304.jobhunter.llm.quota;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.llm.LlmTestModels;
import io.github.quanghung304.jobhunter.llm.ModelTier;
import io.github.quanghung304.jobhunter.llm.registry.LlmRegistryFile;
import io.github.quanghung304.jobhunter.llm.registry.RegisteredModel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CallPacerTest {

    static final Instant T0 = Instant.parse("2026-10-17T12:00:00Z");
    static final RegisteredModel TWO_PER_MINUTE = LlmTestModels.withLimits("slow", ModelTier.FREE,
            new LlmRegistryFile.Limits(2, null, null, null, null));

    final MutableClock clock = new MutableClock(T0);
    final List<Duration> sleeps = new ArrayList<>();
    final CallPacer pacer = new CallPacer(clock, duration -> {
        sleeps.add(duration);
        clock.set(clock.instant().plus(duration));
    });

    @Test
    void waitsOnlyWhenTheMinuteIsFull() throws InterruptedException {
        pacer.acquire(TWO_PER_MINUTE);
        clock.set(T0.plusSeconds(10));
        pacer.acquire(TWO_PER_MINUTE);
        clock.set(T0.plusSeconds(20));
        pacer.acquire(TWO_PER_MINUTE);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(40));
        assertThat(clock.instant()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void windowSlides() throws InterruptedException {
        pacer.acquire(TWO_PER_MINUTE);
        pacer.acquire(TWO_PER_MINUTE);
        clock.set(T0.plusSeconds(61));
        pacer.acquire(TWO_PER_MINUTE);

        assertThat(sleeps).isEmpty();
    }

    @Test
    void modelsWithoutRpmAreNotPaced() throws InterruptedException {
        RegisteredModel free = LlmTestModels.withLimits("free", ModelTier.FREE, LlmTestModels.NO_LIMITS);
        for (int i = 0; i < 100; i++) {
            pacer.acquire(free);
        }

        assertThat(sleeps).isEmpty();
    }

    @Test
    void modelsArePacedSeparately() throws InterruptedException {
        RegisteredModel other = LlmTestModels.withLimits("other", ModelTier.FREE,
                new LlmRegistryFile.Limits(2, null, null, null, null));
        pacer.acquire(TWO_PER_MINUTE);
        pacer.acquire(TWO_PER_MINUTE);
        pacer.acquire(other);
        pacer.acquire(other);

        assertThat(sleeps).isEmpty();
    }
}
