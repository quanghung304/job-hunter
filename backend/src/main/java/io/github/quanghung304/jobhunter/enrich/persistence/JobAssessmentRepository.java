package io.github.quanghung304.jobhunter.enrich.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAssessmentRepository extends JpaRepository<JobAssessment, Long> {

    Optional<JobAssessment> findByJobIdAndProfileIdAndScorePromptVersion(
            Long jobId, String profileId, String scorePromptVersion);

    List<JobAssessment> findByJobId(Long jobId);

    /** Rows of a profile and score prompt version evaluated with another profile file. */
    List<JobAssessment> findByProfileIdAndScorePromptVersionAndProfileHashNotOrderById(
            String profileId, String scorePromptVersion, String profileHash);
}
