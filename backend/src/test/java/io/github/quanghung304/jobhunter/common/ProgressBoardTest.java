package io.github.quanghung304.jobhunter.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.quanghung304.jobhunter.common.ProgressBoard.Progress;
import io.github.quanghung304.jobhunter.common.ProgressBoard.Stage;
import org.junit.jupiter.api.Test;

class ProgressBoardTest {

    private final ProgressBoard board = new ProgressBoard();

    @Test
    void showsTheNewestOpenActivityUntilItCloses() {
        assertThat(board.current()).isEmpty();

        try (ProgressBoard.Activity pipeline = board.begin(Stage.COLLECT, "Starting")) {
            pipeline.update(Stage.COLLECT, "Collect 2/4 sources", 40);
            assertThat(board.current()).contains(new Progress(Stage.COLLECT, "Collect 2/4 sources", 40));

            try (ProgressBoard.Activity scoring = board.begin(Stage.SCORE, "Score")) {
                scoring.update(Stage.SCORE, "Score 3 / 12", 3, 12);
                pipeline.update(Stage.PREFILTER, "Prefilter", 80);
                assertThat(board.current()).contains(new Progress(Stage.SCORE, "Score 3 / 12", 25));
            }

            assertThat(board.current()).contains(new Progress(Stage.PREFILTER, "Prefilter", 80));
        }
        assertThat(board.current()).isEmpty();
    }

    @Test
    void percentIsClampedAndAnEmptyLoopIsDone() {
        try (ProgressBoard.Activity activity = board.begin(Stage.ANALYZE, "Analyze")) {
            activity.update(Stage.ANALYZE, "x", 140);
            assertThat(board.current().orElseThrow().percent()).isEqualTo(100);
            activity.update(Stage.ANALYZE, "x", 0, 0);
            assertThat(board.current().orElseThrow().percent()).isEqualTo(100);
        }
    }
}
