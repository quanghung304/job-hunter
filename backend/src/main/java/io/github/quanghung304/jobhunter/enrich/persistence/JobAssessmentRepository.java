package io.github.quanghung304.jobhunter.enrich.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAssessmentRepository extends JpaRepository<JobAssessment, Long> {

    Optional<JobAssessment> findByJobIdAndProfileIdAndScorePromptVersion(
            Long jobId, String profileId, String scorePromptVersion);
}
