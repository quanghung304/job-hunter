package io.github.quanghung304.jobhunter.enrich.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExtractionRepository extends JpaRepository<JobExtraction, Long> {

    Optional<JobExtraction> findByJobIdAndPromptVersion(Long jobId, String promptVersion);

    List<JobExtraction> findByJobIdInAndPromptVersion(Collection<Long> jobIds, String promptVersion);

    List<JobExtraction> findByJobId(Long jobId);
}
