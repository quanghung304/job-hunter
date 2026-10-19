package io.github.quanghung304.jobhunter.tracking;

import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.HUGH;
import static io.github.quanghung304.jobhunter.tracking.BoardFixtures.OTHER;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.enrich.Eligibility;
import io.github.quanghung304.jobhunter.enrich.JobAttributes.RemoteType;
import io.github.quanghung304.jobhunter.ingest.PostingView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** The job detail of one profile, against Postgres. */
@PostgresJpaTest
@Import(TrackingTestConfig.class)
class JobDetailQueryIT {

    @Autowired
    JobDetailQuery details;

    @Autowired
    TrackingService tracking;

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    BoardFixtures db;

    @BeforeEach
    void setUp() {
        db = new BoardFixtures(jdbc);
    }

    @Test
    void scoredJobHasEverythingTheDetailPageShows() {
        long id = db.job("Java Developer", "Acme", 2);
        db.scored(id, HUGH, 72);
        db.posting(id, "himalayas");
        db.posting(id, "jobicy");
        tracking.changeStatus(id, HUGH, TrackingStatus.SHORTLISTED, "nice");
        tracking.updateNotes(id, HUGH, "Ask about the stack");

        JobDetailView view = details.find(id, HUGH).orElseThrow();

        assertThat(view.summary().score()).isEqualTo(72);
        assertThat(view.summary().scoreBand()).isEqualTo(ScoreBand.GOOD);
        assertThat(view.summary().status()).isEqualTo(TrackingStatus.SHORTLISTED);
        assertThat(view.job().descriptionText()).isEqualTo("Build things.");
        assertThat(view.job().applyUrl()).isEqualTo("https://apply.example/1");
        assertThat(view.job().tags()).containsExactly("java", "spring");
        assertThat(view.attributes().remoteType()).isEqualTo(RemoteType.REMOTE_RESTRICTED);
        assertThat(view.attributes().roleSummary()).isEqualTo("Backend role.");
        assertThat(view.assessment().eligibility()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(view.assessment().scoreDetails().path("strengths").get(0).asText()).isEqualTo("Java");
        assertThat(view.postings()).extracting(PostingView::sourceCode).containsExactly("himalayas", "jobicy");
        assertThat(view.postings().get(0).url()).isEqualTo("https://himalayas.example/jobs/" + id);
        assertThat(view.notes()).isEqualTo("Ask about the stack");
        assertThat(view.history()).extracting(StatusChange::to).containsExactly(TrackingStatus.SHORTLISTED);
        assertThat(view.allowedTransitions()).containsExactlyInAnyOrder(TrackingStatus.DISMISSED,
                TrackingStatus.APPLIED, TrackingStatus.NEW);
    }

    @Test
    void assessmentIsPerProfileWhileTheExtractionIsShared() {
        long id = db.job("Backend Engineer", "Beta", 1);
        db.scored(id, OTHER, 90);

        JobDetailView view = details.find(id, HUGH).orElseThrow();

        assertThat(view.assessment()).as("only the other profile has a row").isNull();
        assertThat(view.summary().score()).isNull();
        assertThat(view.attributes()).as("extraction is profile-independent").isNotNull();
        assertThat(view.summary().analysisStage()).isEqualTo(AnalysisStage.NOT_ANALYZED);
        assertThat(view.notes()).isNull();
        assertThat(view.history()).isEmpty();
        assertThat(view.allowedTransitions()).containsExactlyInAnyOrder(TrackingStatus.SHORTLISTED,
                TrackingStatus.DISMISSED, TrackingStatus.APPLIED);
    }

    @Test
    void failedExtractionHasNoAttributes() {
        long id = db.job("Backend Engineer", "Beta", 1);
        db.passed(id, HUGH);
        db.extraction(id, "FAILED", null);

        JobDetailView view = details.find(id, HUGH).orElseThrow();

        assertThat(view.attributes()).isNull();
        assertThat(view.summary().analysisStage()).isEqualTo(AnalysisStage.FAILED);
    }

    @Test
    void unknownJobIsEmpty() {
        assertThat(details.find(424_242L, HUGH)).isEmpty();
    }
}
