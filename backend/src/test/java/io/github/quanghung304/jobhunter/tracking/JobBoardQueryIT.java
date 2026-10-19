package io.github.quanghung304.jobhunter.tracking;

import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.HUGH;
import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.OTHER;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.common.model.EmploymentType;
import io.github.quanghung304.jobhunter.enrich.CheckCounts;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.JobStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** The board query against Postgres: default view, every filter, sorts, pages and profile isolation. */
@PostgresJpaTest
@Import(TrackingTestConfig.class)
class JobBoardQueryIT {

    @Autowired
    JobBoardQuery board;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    BoardFixtures db;

    @BeforeEach
    void setUp() {
        db = new BoardFixtures(jdbc);
    }

    private List<Long> ids(JobBoardFilter filter) {
        return ids(filter, JobSort.SCORE);
    }

    private List<Long> ids(JobBoardFilter filter, JobSort sort) {
        return board.page(HUGH, filter, sort, 0, 100).items().stream().map(JobBoardRow::id).toList();
    }

    private static JobBoardFilter filter() {
        return JobBoardFilter.NONE;
    }

    @Nested
    class DefaultView {

        @Test
        void showsEverythingButDismissedAndMarkedIneligibleScoredFirst() {
            long top = db.job("Java Developer", "Acme", 5);
            db.scored(top, HUGH, 90);
            long shortlisted = db.job("Kotlin Developer", "Beta", 4);
            db.scored(shortlisted, HUGH, 60);
            db.tracking(shortlisted, HUGH, "SHORTLISTED");
            long applied = db.job("Applied", "Acme", 6);
            db.scored(applied, HUGH, 95);
            db.tracking(applied, HUGH, "APPLIED");
            long newest = db.job("Backend Engineer", "Gamma", 1);
            db.passed(newest, HUGH);
            long failingCheck = db.job("Platform Engineer", "Delta", 3);
            db.analyzed(failingCheck, HUGH, "FAIL");
            long markedEligible = db.job("Data Engineer", "Delta", 7);
            db.analyzed(markedEligible, HUGH, "UNKNOWN");
            db.decide(markedEligible, HUGH, "ELIGIBLE");

            long dismissed = db.job("Dismissed", "Acme", 1);
            db.scored(dismissed, HUGH, 95);
            db.tracking(dismissed, HUGH, "DISMISSED");
            long markedIneligible = db.job("Marked ineligible", "Acme", 1);
            db.scored(markedIneligible, HUGH, 97);
            db.decide(markedIneligible, HUGH, "INELIGIBLE");
            long expired = db.job("Expired", "Acme", 1, "EXPIRED");
            db.scored(expired, HUGH, 99);
            long rejected = db.job("Rejected", "Acme", 1);
            db.rejected(rejected, HUGH);
            db.job("Never prefiltered", "Acme", 1);

            assertThat(ids(filter())).containsExactly(applied, top, shortlisted, newest, failingCheck, markedEligible);
            assertThat(board.countDefaultView(HUGH)).isEqualTo(6);
        }

        @Test
        void usesOnlyTheRowsOfTheRequestedProfile() {
            long otherOnlyScored = db.job("Scored for other", "Acme", 1);
            db.rejected(otherOnlyScored, HUGH);
            db.scored(otherOnlyScored, OTHER, 99);
            db.tracking(otherOnlyScored, OTHER, "SHORTLISTED");
            long dismissedByOther = db.job("Dismissed by other", "Beta", 2);
            db.passed(dismissedByOther, HUGH);
            db.passed(dismissedByOther, OTHER);
            db.tracking(dismissedByOther, OTHER, "DISMISSED");

            assertThat(ids(filter())).containsExactly(dismissedByOther);
            assertThat(board.page(OTHER, filter(), JobSort.SCORE, 0, 10).items())
                    .extracting(JobBoardRow::id, JobBoardRow::score, JobBoardRow::status)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple(otherOnlyScored, 99,
                            TrackingStatus.SHORTLISTED));
            JobBoardRow hughsView = board.find(otherOnlyScored, HUGH).orElseThrow();
            assertThat(hughsView.score()).isNull();
            assertThat(hughsView.status()).isEqualTo(TrackingStatus.NEW);
            assertThat(board.find(dismissedByOther, HUGH).orElseThrow().status()).isEqualTo(TrackingStatus.NEW);
        }
    }

    @Nested
    class Filters {

        @Test
        void status() {
            long applied = db.job("Applied", "Acme", 1);
            db.passed(applied, HUGH);
            db.tracking(applied, HUGH, "APPLIED");
            long fresh = db.job("Fresh", "Acme", 1);
            db.passed(fresh, HUGH);
            long dismissed = db.job("Dismissed", "Acme", 1);
            db.tracking(dismissed, HUGH, "DISMISSED");

            assertThat(ids(withStatuses(Set.of(TrackingStatus.APPLIED)))).containsExactly(applied);
            assertThat(ids(withStatuses(Set.of(TrackingStatus.NEW)))).containsExactly(fresh);
            assertThat(ids(withStatuses(Set.of(TrackingStatus.DISMISSED, TrackingStatus.APPLIED))))
                    .containsExactlyInAnyOrder(applied, dismissed);
        }

        @Test
        void decision() {
            long eligible = db.job("Eligible", "Acme", 1);
            db.analyzed(eligible, HUGH, "OK");
            db.decide(eligible, HUGH, "ELIGIBLE");
            long uncertain = db.job("Uncertain", "Acme", 2);
            db.analyzed(uncertain, HUGH, "UNKNOWN");
            long ineligible = db.job("Ineligible", "Acme", 3);
            db.analyzed(ineligible, HUGH, "OK");
            db.decide(ineligible, HUGH, "INELIGIBLE");

            assertThat(ids(withDecisions(DecisionFilter.INELIGIBLE))).containsExactly(ineligible);
            assertThat(ids(withDecisions(DecisionFilter.UNDECIDED))).containsExactly(uncertain);
            assertThat(ids(withDecisions(DecisionFilter.ELIGIBLE, DecisionFilter.UNDECIDED)))
                    .containsExactly(eligible, uncertain);
        }

        @Test
        void checks() {
            long allOk = db.job("All OK", "Acme", 1);
            db.analyzed(allOk, HUGH, "OK");
            long unknown = db.job("Unknown", "Acme", 2);
            db.analyzed(unknown, HUGH, "UNKNOWN");
            long fail = db.job("Fail", "Acme", 3);
            db.analyzed(fail, HUGH, "FAIL");
            long notChecked = db.job("Not checked", "Acme", 4);
            db.passed(notChecked, HUGH);

            assertThat(ids(withChecks(ChecksFilter.ALL_OK))).containsExactly(allOk);
            assertThat(ids(withChecks(ChecksFilter.HAS_UNKNOWN))).containsExactly(unknown);
            assertThat(ids(withChecks(ChecksFilter.HAS_FAIL))).containsExactly(fail);
            assertThat(ids(withChecks(ChecksFilter.NOT_CHECKED))).containsExactly(notChecked);
            assertThat(ids(withChecks(ChecksFilter.HAS_UNKNOWN, ChecksFilter.HAS_FAIL))).containsExactly(unknown, fail);
            assertThat(board.find(fail, HUGH).orElseThrow().checks()).isEqualTo(new CheckCounts(9, 0, 1));
            assertThat(board.find(notChecked, HUGH).orElseThrow().checks()).isNull();
        }

        @Test
        void scoreRange() {
            long high = db.job("High", "Acme", 1);
            db.scored(high, HUGH, 88);
            long mid = db.job("Mid", "Acme", 1);
            db.scored(mid, HUGH, 70);
            long low = db.job("Low", "Acme", 1);
            db.scored(low, HUGH, 40);
            db.passed(db.job("Unscored", "Acme", 1), HUGH);

            assertThat(ids(new JobBoardFilter(null, null, null, 70, null, null, null, null, null, false, null)))
                    .containsExactly(high, mid);
            assertThat(ids(new JobBoardFilter(null, null, null, null, 69, null, null, null, null, false, null)))
                    .containsExactly(low);
            assertThat(ids(new JobBoardFilter(null, null, null, 50, 80, null, null, null, null, false, null)))
                    .containsExactly(mid);
        }

        @Test
        void source() {
            long both = db.job("Both", "Acme", 1);
            db.posting(both, "himalayas");
            db.posting(both, "arbeitnow");
            long jobicy = db.job("Jobicy", "Acme", 2);
            db.posting(jobicy, "jobicy");

            assertThat(ids(new JobBoardFilter(null, null, null, null, null, Set.of("arbeitnow"), null, null, null, false,
                    null))).containsExactly(both);
            assertThat(ids(new JobBoardFilter(null, null, null, null, null, Set.of("jobicy", "himalayas"), null, null, null,
                    false, null))).containsExactly(both, jobicy);
        }

        @Test
        void employmentTypeComesFromTheExtraction() {
            long contract = db.job("Contract", "Acme", 1);
            db.extraction(contract);
            long partTime = db.job("Part time", "Acme", 2);
            db.extraction(partTime, "OK", BoardFixtures.ATTRIBUTES.replace("\"CONTRACT\",\"FULL_TIME\"",
                    "\"PART_TIME\""));
            db.job("Not analyzed", "Acme", 3);

            assertThat(ids(new JobBoardFilter(null, null, null, null, null, null, Set.of(EmploymentType.CONTRACT), null,
                    null, false, null))).containsExactly(contract);
            assertThat(ids(new JobBoardFilter(null, null, null, null, null, null,
                    Set.of(EmploymentType.PART_TIME, EmploymentType.FULL_TIME), null, null, false, null)))
                    .containsExactly(contract, partTime);
        }

        @Test
        void postedWithinDays() {
            long recent = db.job("Recent", "Acme", 2);
            db.job("Old", "Acme", 10);

            assertThat(ids(new JobBoardFilter(null, null, null, null, null, null, null, 7, null, false, null)))
                    .containsExactly(recent);
        }

        @Test
        void textSearchInTitleOrCompanyIgnoresCaseAndTreatsWildcardsLiterally() {
            long byTitle = db.job("Senior JAVA Developer", "Acme", 1);
            long byCompany = db.job("Backend Engineer", "Javalin GmbH", 2);
            db.job("Frontend Engineer", "Beta", 3);
            long percent = db.job("100% remote", "Gamma", 4);

            assertThat(ids(withQ("java"))).containsExactly(byTitle, byCompany);
            assertThat(ids(withQ("  acme "))).containsExactly(byTitle);
            assertThat(ids(withQ("100%"))).containsExactly(percent);
            assertThat(ids(withQ("%"))).containsExactly(percent);
            assertThat(ids(withQ("_"))).isEmpty();
        }

        @Test
        void expiredJobsOnlyWhenIncluded() {
            long active = db.job("Active", "Acme", 1);
            long expired = db.job("Expired", "Acme", 2, "EXPIRED");

            assertThat(ids(withQ("e"))).containsExactly(active);
            assertThat(ids(new JobBoardFilter(null, null, null, null, null, null, null, null, null, true, null)))
                    .containsExactly(active, expired);
        }

        @Test
        void analysisStage() {
            long notAnalyzed = db.job("Not analyzed", "Acme", 1);
            db.passed(notAnalyzed, HUGH);
            long noRow = db.job("No row", "Acme", 2);
            long analyzed = db.job("Analyzed", "Acme", 3);
            db.analyzed(analyzed, HUGH, "OK");
            long scored = db.job("Scored", "Acme", 4);
            db.scored(scored, HUGH, 75);
            long extractFailed = db.job("Extraction failed", "Acme", 5);
            db.passed(extractFailed, HUGH);
            db.extraction(extractFailed, "FAILED", null);
            long scoreFailed = db.job("Score failed", "Acme", 6);
            db.scoreFailed(scoreFailed, HUGH);

            assertThat(ids(withStage(AnalysisStage.NOT_ANALYZED))).containsExactly(notAnalyzed, noRow);
            assertThat(ids(withStage(AnalysisStage.ANALYZED))).containsExactly(analyzed);
            assertThat(ids(withStage(AnalysisStage.SCORED))).containsExactly(scored);
            assertThat(ids(withStage(AnalysisStage.FAILED))).containsExactly(extractFailed, scoreFailed);
        }

        @Test
        void filtersCombine() {
            long match = db.job("Java Developer", "Acme", 1);
            db.scored(match, HUGH, 80);
            db.posting(match, "himalayas");
            long wrongSource = db.job("Java Engineer", "Acme", 1);
            db.scored(wrongSource, HUGH, 80);
            db.posting(wrongSource, "jobicy");

            assertThat(ids(new JobBoardFilter(Set.of(TrackingStatus.NEW), Set.of(DecisionFilter.UNDECIDED),
                    Set.of(ChecksFilter.ALL_OK), 70, null,
                    Set.of("himalayas"), Set.of(EmploymentType.CONTRACT), 3, "java", false,
                    Set.of(AnalysisStage.SCORED)))).containsExactly(match);
        }
    }

    @Test
    void sortByPostedIgnoresTheScore() {
        long scoredOld = db.job("Scored old", "Acme", 5);
        db.scored(scoredOld, HUGH, 95);
        long freshUnscored = db.job("Fresh", "Acme", 1);
        db.passed(freshUnscored, HUGH);

        assertThat(ids(filter(), JobSort.SCORE)).containsExactly(scoredOld, freshUnscored);
        assertThat(ids(filter(), JobSort.POSTED)).containsExactly(freshUnscored, scoredOld);
    }

    @Test
    void pagination() {
        List<Long> all = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            long id = db.job("Job " + i, "Acme", i);
            db.passed(id, HUGH);
            all.add(id);
        }

        JobBoardPage first = board.page(HUGH, filter(), JobSort.POSTED, 0, 2);
        JobBoardPage last = board.page(HUGH, filter(), JobSort.POSTED, 2, 2);
        JobBoardPage beyond = board.page(HUGH, filter(), JobSort.POSTED, 3, 2);

        assertThat(first.items()).extracting(JobBoardRow::id).containsExactly(all.get(0), all.get(1));
        assertThat(first.totalItems()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(last.items()).extracting(JobBoardRow::id).containsExactly(all.get(4));
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalItems()).isEqualTo(5);
    }

    @Test
    void rowCarriesExtractionSourcesAndBand() {
        long id = db.job("Java Developer", "Acme", 2);
        db.scored(id, HUGH, 86);
        db.posting(id, "jobicy");
        db.posting(id, "himalayas");
        db.posting(id, "himalayas");

        JobBoardRow row = board.find(id, HUGH).orElseThrow();

        assertThat(row.title()).isEqualTo("Java Developer");
        assertThat(row.company()).isEqualTo("Acme");
        assertThat(row.locationText()).isEqualTo("Remote (EU)");
        assertThat(row.score()).isEqualTo(86);
        assertThat(row.scoreBand()).isEqualTo(ScoreBand.EXCELLENT);
        assertThat(row.summary()).isEqualTo("Good match 86");
        assertThat(row.eligibilityDecision()).as("undecided").isNull();
        assertThat(row.checks()).isEqualTo(new CheckCounts(10, 0, 0));
        assertThat(row.remoteType()).isEqualTo(RemoteType.REMOTE_RESTRICTED);
        assertThat(row.allowedRegions()).containsExactly("EU", "IT");
        assertThat(row.employmentTypes()).containsExactly(EmploymentType.CONTRACT, EmploymentType.FULL_TIME);
        assertThat(row.sourceCodes()).containsExactly("himalayas", "jobicy");
        assertThat(row.postedAt()).isEqualTo(BoardFixtures.NOW.minus(java.time.Duration.ofDays(2)));
        assertThat(row.jobStatus()).isEqualTo(JobStatus.ACTIVE);
        assertThat(row.analysisStage()).isEqualTo(AnalysisStage.SCORED);
        assertThat(board.find(424_242L, HUGH)).isEmpty();
    }

    private static JobBoardFilter withDecisions(DecisionFilter... decisions) {
        return new JobBoardFilter(null, Set.of(decisions), null, null, null, null, null, null, null, false, null);
    }

    private static JobBoardFilter withChecks(ChecksFilter... checks) {
        return new JobBoardFilter(null, null, Set.of(checks), null, null, null, null, null, null, false, null);
    }

    private static JobBoardFilter withStatuses(Set<TrackingStatus> statuses) {
        return new JobBoardFilter(statuses, null, null, null, null, null, null, null, null, false, null);
    }

    private static JobBoardFilter withQ(String q) {
        return new JobBoardFilter(null, null, null, null, null, null, null, null, q, false, null);
    }

    private static JobBoardFilter withStage(AnalysisStage stage) {
        return new JobBoardFilter(null, null, null, null, null, null, null, null, null, false, Set.of(stage));
    }
}
