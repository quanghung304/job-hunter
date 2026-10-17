package io.github.quanghung304.jobhunter.ingest.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByFingerprint(String fingerprint);

    List<Job> findAllByIdInOrderById(Collection<Long> ids);

    /** The job with this fingerprint and its re-postings ({@code <fp>#yyyyMMdd}), newest first. */
    @Query("""
            select j from Job j
            where j.fingerprint = :fingerprint or j.fingerprint like concat(:fingerprint, '#%')
            order by j.firstSeenAt desc, j.id desc""")
    List<Job> findFingerprintFamily(@Param("fingerprint") String fingerprint);
}
