package io.github.quanghung304.jobhunter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/**
 * Helpers for repository ITs. Rows of other modules' tables (e.g. a {@code job} for a foreign
 * key) are inserted with SQL, so tests respect module boundaries like the main code.
 */
public final class PersistenceFixtures {

    public static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

    private PersistenceFixtures() {
    }

    /** Inserts a minimal {@code job} row and returns its id. */
    public static long insertJob(EntityManager em, String fingerprint) {
        Object id = em.createNativeQuery("""
                        insert into job (fingerprint, title, company_name, company_key, description_text,
                                         first_seen_at, last_seen_at)
                        values (:fp, 'Java Developer', 'Acme', 'acme', 'Build things.', :t, :t)
                        returning id""")
                .setParameter("fp", fingerprint)
                .setParameter("t", T0)
                .getSingleResult();
        return ((Number) id).longValue();
    }

    /** Runs a native statement and expects PostgreSQL to reject it with the given constraint. */
    public static void assertRejected(EntityManager em, String constraintKind, String sql) {
        assertRejected(constraintKind, () -> em.createNativeQuery(sql).executeUpdate());
    }

    /** {@code constraintKind}: "unique", "check", "foreign key" or "not-null". */
    public static void assertRejected(String constraintKind, ThrowingCallable call) {
        assertThatThrownBy(call).rootCause().hasMessageContaining(
                constraintKind.equals("not-null") ? "not-null constraint" : "violates " + constraintKind + " constraint");
    }
}
