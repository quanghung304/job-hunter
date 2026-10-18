package io.github.quanghung304.jobhunter.pipeline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Postgres session advisory lock so only one pipeline run happens at a time, also across
 * processes (overview.md "Scheduling"). The lock lives on one pooled connection held for the
 * whole run and is released by {@link Handle#close()}.
 */
@Component
public class PipelineLock {

    /** Arbitrary constant identifying the pipeline lock in {@code pg_locks}. */
    static final long KEY = 7_013_001L;

    private final DataSource dataSource;

    public PipelineLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** The lock, or empty when another session holds it. */
    public Optional<Handle> tryAcquire() {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            if (call(connection, "select pg_try_advisory_lock(?)")) {
                return Optional.of(new Handle(connection));
            }
            connection.close();
            return Optional.empty();
        } catch (SQLException e) {
            closeQuietly(connection);
            throw new IllegalStateException("cannot take the pipeline lock", e);
        }
    }

    private static boolean call(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nothing left to do
            }
        }
    }

    /** A held lock; closing it unlocks and returns the connection to the pool. */
    public static final class Handle implements AutoCloseable {
        private final Connection connection;

        private Handle(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void close() {
            try {
                call(connection, "select pg_advisory_unlock(?)");
            } catch (SQLException e) {
                throw new IllegalStateException("cannot release the pipeline lock", e);
            } finally {
                closeQuietly(connection);
            }
        }
    }
}
