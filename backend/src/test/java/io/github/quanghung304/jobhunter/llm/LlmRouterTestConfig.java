package io.github.quanghung304.jobhunter.llm;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.common.Sleeper;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.json.JsonExtractor;
import io.github.quanghung304.jobhunter.llm.prompt.PromptRepository;
import io.github.quanghung304.jobhunter.llm.quota.CallPacer;
import io.github.quanghung304.jobhunter.llm.quota.QuotaTracker;
import io.github.quanghung304.jobhunter.llm.registry.ModelRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The llm module wired for {@code @PostgresJpaTest}s: real router, quota tracker, budget guard
 * and call log; the {@link FakeLlmClient} serves every model of {@code llm-registry/router-test.yaml}.
 * Sleeps are recorded and advance the clock instead of waiting.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({LlmRouter.class, AssignmentService.class, BudgetGuard.class, LlmCallLogger.class, QuotaTracker.class,
        CallPacer.class, PromptRepository.class, JsonExtractor.class})
public class LlmRouterTestConfig {

    public static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    public static final int MAX_CALLS_PER_RUN = 20;
    public static final BigDecimal MONTHLY_BUDGET = new BigDecimal("1.00");

    @Bean
    MutableClock clock() {
        return new MutableClock(NOW);
    }

    @Bean
    RecordingSleeper sleeper(MutableClock clock) {
        return new RecordingSleeper(clock);
    }

    @Bean
    Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    /** Paid calls are allowed here unless a test sets {@code test.llm.allow-paid=false}. */
    @Bean
    LlmProperties llmProperties(@Value("${test.llm.allow-paid:true}") boolean allowPaidCalls) {
        return properties(null, allowPaidCalls);
    }

    @Bean
    ModelRegistry modelRegistry(Validator validator) throws Exception {
        Path file = Path.of(LlmRouterTestConfig.class.getResource("/llm-registry/router-test.yaml").toURI());
        return ModelRegistry.load(file, validator, Map.<String, String>of()::get);
    }

    @Bean
    FakeLlmClient fakeLlmClient() {
        return FakeLlmClient.simulating(null);
    }

    public static LlmProperties properties(String forceModel, boolean allowPaidCalls) {
        return new LlmProperties("unused", forceModel, "extract-v1", "score-v2", MAX_CALLS_PER_RUN, MONTHLY_BUDGET,
                allowPaidCalls, Duration.ofSeconds(60), Duration.ofSeconds(180), new LlmProperties.Fake(null));
    }

    /** Records sleeps and moves the clock forward by them. */
    public static final class RecordingSleeper implements Sleeper {
        private final MutableClock clock;
        private final List<Duration> sleeps = new ArrayList<>();

        RecordingSleeper(MutableClock clock) {
            this.clock = clock;
        }

        @Override
        public synchronized void sleep(Duration duration) {
            sleeps.add(duration);
            clock.advance(duration);
        }

        public synchronized List<Duration> sleeps() {
            return List.copyOf(sleeps);
        }

        public synchronized void clear() {
            sleeps.clear();
        }
    }
}
