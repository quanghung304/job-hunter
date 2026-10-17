package io.github.quanghung304.jobhunter.enrich.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExtractionRepository extends JpaRepository<JobExtraction, Long> {

    Optional<JobExtraction> findByJobIdAndPromptVersion(Long jobId, String promptVersion);
}
