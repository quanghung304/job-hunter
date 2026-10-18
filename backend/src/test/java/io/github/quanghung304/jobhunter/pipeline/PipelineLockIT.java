package io.github.quanghung304.jobhunter.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** One run at a time: the advisory lock blocks a second run, also from another session. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "app.profile.path=../config/profile.example.yaml",
        "app.sources.arbeitnow.enabled=false", "app.sources.himalayas.enabled=false",
        "app.sources.jobicy.enabled=false", "app.sources.weworkremotely.enabled=false"})
@Import(TestcontainersConfiguration.class)
class PipelineLockIT {

    @Autowired
    PipelineOrchestrator pipeline;

    @Autowired
    PipelineLock lock;

    @Autowired
    DataSource dataSource;

    @Test
    void lockHeldByAnotherSessionBlocksTheRun() throws Exception {
        try (Connection other = dataSource.getConnection()) {
            assertThat(advisoryLock(other, "select pg_try_advisory_lock(?)")).isTrue();

            assertThatThrownBy(() -> pipeline.runOnce(Trigger.MANUAL))
                    .isInstanceOf(PipelineAlreadyRunningException.class);
            assertThat(pipeline.isRunning()).isFalse();

            assertThat(advisoryLock(other, "select pg_advisory_unlock(?)")).isTrue();
        }

        assertThat(pipeline.runOnce(Trigger.MANUAL).status()).isNotNull();
    }

    @Test
    void lockIsReleasedAfterEachRun() {
        pipeline.runOnce(Trigger.MANUAL);

        try (PipelineLock.Handle handle = lock.tryAcquire().orElseThrow()) {
            assertThat(lock.tryAcquire()).as("a second session cannot take it meanwhile").isEmpty();
        }
        assertThat(lock.tryAcquire()).hasValueSatisfying(PipelineLock.Handle::close);
    }

    private static boolean advisoryLock(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, PipelineLock.KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
