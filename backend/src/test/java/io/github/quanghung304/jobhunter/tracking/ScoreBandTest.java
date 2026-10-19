package io.github.quanghung304.jobhunter.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoreBandTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "100, EXCELLENT", "85, EXCELLENT", "84, GOOD", "70, GOOD", "69, MAYBE", "50, MAYBE", "49, WEAK",
            "0, WEAK", "null, NONE"})
    void bandBoundaries(Integer score, ScoreBand band) {
        assertThat(ScoreBand.of(score)).isEqualTo(band);
    }
}
