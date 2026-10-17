package io.github.quanghung304.jobhunter.enrich.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.quanghung304.jobhunter.enrich.AssessmentStatus;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.enrich.PrefilterResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Prefilter, eligibility and score of one job for one profile. Score fields are written by
 * {@code scoring} only through {@code enrich}'s {@code AssessmentService}.
 */
@Entity
@Table(name = "job_assessment")
public class JobAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "profile_id", nullable = false, updatable = false)
    private String profileId;

    @Column(name = "profile_hash", nullable = false)
    private String profileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "prefilter_result")
    private PrefilterResult prefilterResult;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "prefilter_reasons", nullable = false)
    private List<String> prefilterReasons = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility")
    private Eligibility eligibility;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "eligibility_reasons", nullable = false)
    private List<String> eligibilityReasons = new ArrayList<>();

    @Column(name = "extraction_id")
    private Long extractionId;

    @Column(name = "score")
    private Integer score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown")
    private JsonNode scoreBreakdown;

    @Column(name = "summary")
    private String summary;

    @Column(name = "score_prompt_version")
    private String scorePromptVersion;

    @Column(name = "score_model")
    private String scoreModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AssessmentStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobAssessment() {
        // for JPA
    }

    public JobAssessment(
            Long jobId,
            String profileId,
            String profileHash,
            AssessmentStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.jobId = jobId;
        this.profileId = profileId;
        this.profileHash = profileHash;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getJobId() {
        return jobId;
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileHash() {
        return profileHash;
    }

    public void setProfileHash(String profileHash) {
        this.profileHash = profileHash;
    }

    public PrefilterResult getPrefilterResult() {
        return prefilterResult;
    }

    public void setPrefilterResult(PrefilterResult prefilterResult) {
        this.prefilterResult = prefilterResult;
    }

    /** Unmodifiable. */
    public List<String> getPrefilterReasons() {
        return List.copyOf(prefilterReasons);
    }

    public void setPrefilterReasons(List<String> prefilterReasons) {
        this.prefilterReasons = new ArrayList<>(prefilterReasons);
    }

    public Eligibility getEligibility() {
        return eligibility;
    }

    public void setEligibility(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    /** Unmodifiable. */
    public List<String> getEligibilityReasons() {
        return List.copyOf(eligibilityReasons);
    }

    public void setEligibilityReasons(List<String> eligibilityReasons) {
        this.eligibilityReasons = new ArrayList<>(eligibilityReasons);
    }

    public Long getExtractionId() {
        return extractionId;
    }

    public void setExtractionId(Long extractionId) {
        this.extractionId = extractionId;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public JsonNode getScoreBreakdown() {
        return scoreBreakdown;
    }

    public void setScoreBreakdown(JsonNode scoreBreakdown) {
        this.scoreBreakdown = scoreBreakdown;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getScorePromptVersion() {
        return scorePromptVersion;
    }

    public void setScorePromptVersion(String scorePromptVersion) {
        this.scorePromptVersion = scorePromptVersion;
    }

    public String getScoreModel() {
        return scoreModel;
    }

    public void setScoreModel(String scoreModel) {
        this.scoreModel = scoreModel;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
