package io.github.quanghung304.jobhunter.enrich;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.ingest.JobQueryService;
import io.github.quanghung304.jobhunter.llm.LlmRouterTestConfig;
import io.github.quanghung304.jobhunter.profile.ProfileFixtures;
import io.github.quanghung304.jobhunter.profile.ProfileProvider;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * enrich services for {@code @PostgresJpaTest}s on top of {@link LlmRouterTestConfig}: the
 * real router with the fake client (EXTRACT model {@code primary}) and the example profile.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({LlmRouterTestConfig.class, ExtractionService.class, AnalysisService.class, AssessmentService.class,
        Prefilter.class, EligibilityEvaluator.class, JobQueryService.class})
public class EnrichTestConfig {

    @Bean
    ProfileProvider profileProvider() {
        return ProfileFixtures.load(ProfileFixtures.EXAMPLE);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /** Inserts an ACTIVE remote job posted a day before the test clock; returns its id. */
    public static long insertJob(EntityManager em, String fingerprint, String title, String description) {
        return insertJob(em, fingerprint, title, description, "ACTIVE");
    }

    public static long insertJob(EntityManager em, String fingerprint, String title, String description,
            String status) {
        Instant now = LlmRouterTestConfig.NOW;
        Object id = em.createNativeQuery("""
                        insert into job (fingerprint, title, company_name, company_key, location_text, remote_hint,
                                         description_text, posted_at, first_seen_at, last_seen_at, status)
                        values (:fp, :title, 'Acme', 'acme', 'Remote (EU)', 'REMOTE', :description, :posted, :t, :t,
                                :status)
                        returning id""")
                .setParameter("fp", fingerprint)
                .setParameter("title", title)
                .setParameter("description", description)
                .setParameter("posted", now.minusSeconds(86_400))
                .setParameter("t", now)
                .setParameter("status", status)
                .getSingleResult();
        return ((Number) id).longValue();
    }

    static void resetClock(MutableClock clock) {
        clock.set(LlmRouterTestConfig.NOW);
    }
}
