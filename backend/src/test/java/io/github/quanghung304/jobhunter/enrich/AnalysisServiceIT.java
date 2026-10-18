package io.github.quanghung304.jobhunter.enrich;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.MutableClock;
import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessment;
import io.github.quanghung304.jobhunter.enrich.persistence.JobAssessmentRepository;
import io.github.quanghung304.jobhunter.enrich.persistence.JobExtractionRepository;
import io.github.quanghung304.jobhunter.ingest.JobNotFoundException;
import io.github.quanghung304.jobhunter.llm.BudgetGuard;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient.Step;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** The owner's Analyze action (ADR-0010), single and bulk, against Postgres and the fake LLM. */
@PostgresJpaTest
@Import(EnrichTestConfig.class)
class AnalysisServiceIT {

    static final String DESCRIPTION = "Contract role, fully remote in Europe. Java, Spring Boot, SQL.";
    static final String INELIGIBLE_ANSWER = """
            {"remoteType":"REMOTE_RESTRICTED","allowedRegions":["US"],"employmentTypes":["CONTRACT"],
             "requiredSkills":["Java"],"roleSummary":"US-only role.","isAgencyOrReposting":false,
             "isScamSignal":false,"confidence":"HIGH"}""";

    @Autowired
    AnalysisService analysis;

    @Autowired
    JobAssessmentRepository assessments;

    @Autowired
    JobExtractionRepository extractions;

    @Autowired
    FakeLlmClient fake;

    @Autowired
    BudgetGuard budget;

    @Autowired
    MutableClock clock;

    @Autowired
    TestEntityManager em;

    @BeforeEach
    void setUp() {
        fake.reset();
        EnrichTestConfig.resetClock(clock);
    }

    @Test
    void analyzingThreeJobsMakesAtMostThreeCallsAndRepeatingMakesNone() {
        List<Long> ids = List.of(job("a"), job("b"), job("c"));

        List<AnalysisResult> first = ids.stream().map(analysis::analyze).toList();
        List<AnalysisResult> again = ids.stream().map(analysis::analyze).toList();

        assertThat(first).allSatisfy(r -> {
            assertThat(r.outcome()).isEqualTo(AnalysisResult.Outcome.ANALYZED);
            assertThat(r.eligibility()).isEqualTo(Eligibility.ELIGIBLE);
            assertThat(r.llmCalled()).isTrue();
        });
        assertThat(again).allSatisfy(r -> {
            assertThat(r.outcome()).isEqualTo(AnalysisResult.Outcome.ANALYZED);
            assertThat(r.llmCalled()).isFalse();
        });
        assertThat(fake.requests()).hasSize(3);
        JobAssessment row = assessment(ids.get(0));
        assertThat(row.getPrefilterResult()).isEqualTo(PrefilterResult.PASS);
        assertThat(row.getEligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(row.getExtractionId()).isNotNull();
        assertThat(row.getStatus()).as("waits for the owner's Score").isEqualTo(AssessmentStatus.PENDING);
    }

    @Test
    void ineligibleJobIsDone() {
        long id = job("us");
        fake.enqueue(Step.text(INELIGIBLE_ANSWER));

        AnalysisResult result = analysis.analyze(id);

        assertThat(result.eligibility()).isEqualTo(Eligibility.INELIGIBLE);
        assertThat(result.eligibilityReasons()).containsExactly("REGION:FAIL");
        assertThat(assessment(id).getStatus()).isEqualTo(AssessmentStatus.DONE);
    }

    @Test
    void prefilterRejectedJobIsNeverSent() {
        long id = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-ios", "iOS Engineer", "Swift and UIKit.");

        AnalysisResult result = analysis.analyze(id);

        assertThat(result.outcome()).isEqualTo(AnalysisResult.Outcome.PREFILTER_REJECTED);
        assertThat(result.llmCalled()).isFalse();
        assertThat(fake.requests()).isEmpty();
        assertThat(assessment(id).getPrefilterReasons()).contains("TITLE_EXCLUDED");
    }

    @Test
    void pausedModelLeavesTheJobUnanalyzed() {
        long id = job("p");
        fake.enqueue(Step.dailyQuota(null));

        AnalysisResult result = analysis.analyze(id);

        assertThat(result.outcome()).isEqualTo(AnalysisResult.Outcome.LLM_PAUSED);
        assertThat(extractions.findByJobId(id)).isEmpty();
        assertThat(assessment(id).getEligibility()).isNull();
    }

    @Test
    void failedAttemptIsReported() {
        long id = job("f");
        fake.enqueue(Step.text("nope"), Step.text("nope again"));

        AnalysisResult result = analysis.analyze(id);

        assertThat(result.outcome()).isEqualTo(AnalysisResult.Outcome.FAILED);
        assertThat(result.llmCalled()).isTrue();
    }

    @Test
    void unknownJob() {
        assertThatThrownBy(() -> analysis.analyze(999_999L)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void bulkDryRunCountsWithoutCalling() {
        long analyzed = job("done");
        analysis.analyze(analyzed);
        fake.reset();
        long fresh = job("new");
        long expired = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-exp", "Java Developer", DESCRIPTION,
                "EXPIRED");
        long rejected = EnrichTestConfig.insertJob(em.getEntityManager(), "fp-rej", "Android Developer", "Kotlin.");

        BulkAnalysisReport report = analysis.analyzeMany(List.of(analyzed, fresh, expired, rejected, 424_242L), true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.selected()).isEqualTo(5);
        assertThat(report.willAnalyze()).isEqualTo(1);
        assertThat(report.estimatedCalls()).isEqualTo(1);
        assertThat(report.skipped()).containsEntry("alreadyAnalyzed", 1).containsEntry("expired", 1)
                .containsEntry("prefilterRejected", 1).containsEntry("notFound", 1);
        assertThat(fake.requests()).isEmpty();
        assertThat(extractions.findByJobId(fresh)).isEmpty();
    }

    @Test
    void bulkRunNeverSendsAnAlreadyAnalyzedJob() {
        long analyzed = job("done2");
        analysis.analyze(analyzed);
        fake.reset();
        long a = job("x1");
        long b = job("x2");

        BulkAnalysisReport report = analysis.analyzeMany(List.of(analyzed, a, b), false);

        assertThat(report.analyzed()).isEqualTo(2);
        assertThat(report.stoppedBy()).isNull();
        assertThat(fake.requests()).extracting(r -> r.jobId()).containsExactly(a, b);
    }

    @Test
    void bulkRunStopsAtAPauseAndReportsTheRest() {
        long a = job("s1");
        long b = job("s2");
        long c = job("s3");
        fake.enqueue(Step.canned(), Step.dailyQuota(null));

        BulkAnalysisReport report = analysis.analyzeMany(List.of(a, b, c), false);

        assertThat(report.analyzed()).isEqualTo(1);
        assertThat(report.stoppedBy()).isEqualTo(AnalysisResult.Outcome.LLM_PAUSED);
        assertThat(report.notProcessed()).containsExactly(b, c);
        assertThat(fake.requests()).hasSize(2);
    }

    @Test
    void bulkRunIsLimitedTo200Jobs() {
        List<Long> ids = LongStream.rangeClosed(1, 201).boxed().toList();

        assertThatThrownBy(() -> analysis.analyzeMany(ids, true))
                .isInstanceOf(TooManyJobsException.class)
                .hasMessageContaining("201 jobs selected; at most 200");
    }

    private long job(String suffix) {
        return EnrichTestConfig.insertJob(em.getEntityManager(), "fp-" + suffix, "Senior Java Developer", DESCRIPTION);
    }

    private JobAssessment assessment(long jobId) {
        em.flush();
        em.clear();
        return assessments.findByJobIdAndProfileIdAndScorePromptVersion(jobId, "hugh", "score-v1").orElseThrow();
    }
}
