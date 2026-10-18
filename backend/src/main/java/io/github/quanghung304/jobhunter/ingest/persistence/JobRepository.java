package io.github.quanghung304.jobhunter.ingest.persistence;

import io.github.quanghung304.jobhunter.ingest.JobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByFingerprint(String fingerprint);

    List<Job> findAllByIdInOrderById(Collection<Long> ids);

    @Query("select j.id from Job j where j.status = :status order by j.id")
    List<Long> findIdsByStatus(@Param("status") JobStatus status);

    @Query("select j.id from Job j where j.firstSeenAt >= :since order by j.id")
    List<Long> findIdsFirstSeenSince(@Param("since") Instant since);

    /** Marks ACTIVE jobs last seen before {@code cutoff} as EXPIRED; returns the number changed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Job j set j.status = :expired where j.status = :active and j.lastSeenAt < :cutoff")
    int expireNotSeenSince(@Param("cutoff") Instant cutoff, @Param("active") JobStatus active,
            @Param("expired") JobStatus expired);

    /** The job with this fingerprint and its re-postings ({@code <fp>#yyyyMMdd}), newest first. */
    @Query("""
            select j from Job j
            where j.fingerprint = :fingerprint or j.fingerprint like concat(:fingerprint, '#%')
            order by j.firstSeenAt desc, j.id desc""")
    List<Job> findFingerprintFamily(@Param("fingerprint") String fingerprint);
}
