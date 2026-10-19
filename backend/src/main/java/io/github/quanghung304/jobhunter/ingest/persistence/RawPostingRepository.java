package io.github.quanghung304.jobhunter.ingest.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawPostingRepository extends JpaRepository<RawPosting, Long> {

    Optional<RawPosting> findBySourceCodeAndExternalId(String sourceCode, String externalId);

    List<RawPosting> findByJobId(Long jobId);

    List<RawPosting> findByJobIdOrderByFirstSeenAtAscIdAsc(Long jobId);
}
