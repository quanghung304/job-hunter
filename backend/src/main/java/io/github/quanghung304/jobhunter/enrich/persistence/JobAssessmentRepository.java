package io.github.quanghung304.jobhunter.enrich.persistence;

import io.github.quanghung304.jobhunter.enrich.AssessmentStatus;
import io.github.quanghung304.jobhunter.enrich.PrefilterResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobAssessmentRepository extends JpaRepository<JobAssessment, Long> {

    Optional<JobAssessment> findByJobIdAndProfileIdAndScorePromptVersion(
            Long jobId, String profileId, String scorePromptVersion);

    List<JobAssessment> findByJobId(Long jobId);

    List<JobAssessment> findByProfileIdAndScorePromptVersionAndJobIdIn(
            String profileId, String scorePromptVersion, Collection<Long> jobIds);

    @Query("""
            select a.jobId from JobAssessment a
            where a.profileId = :profileId and a.scorePromptVersion = :version and a.score is not null
            order by a.jobId""")
    List<Long> findScoredJobIds(@Param("profileId") String profileId, @Param("version") String version);

    /** Unscored rows whose last scoring attempt failed. */
    List<JobAssessment> findByProfileIdAndScorePromptVersionAndStatusAndScoreIsNullOrderByJobId(
            String profileId, String scorePromptVersion, AssessmentStatus status);

    long countByProfileIdAndScorePromptVersionAndPrefilterResultAndCreatedAtGreaterThanEqual(
            String profileId, String scorePromptVersion, PrefilterResult prefilterResult, Instant since);

    long countByProfileIdAndScorePromptVersionAndScoreIsNotNullAndUpdatedAtGreaterThanEqual(
            String profileId, String scorePromptVersion, Instant since);

    /** Rows of a profile and score prompt version evaluated with another profile file. */
    List<JobAssessment> findByProfileIdAndScorePromptVersionAndProfileHashNotOrderById(
            String profileId, String scorePromptVersion, String profileHash);
}
