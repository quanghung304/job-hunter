package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.enrich.EnrichTestConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** tracking services for {@code @PostgresJpaTest}s, on top of {@link EnrichTestConfig}. */
@TestConfiguration(proxyBeanMethods = false)
@Import({EnrichTestConfig.class, JobBoardQuery.class, JobDetailQuery.class, TrackingService.class})
class TrackingTestConfig {
}
