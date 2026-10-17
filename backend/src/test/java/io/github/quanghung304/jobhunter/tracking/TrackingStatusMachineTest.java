package io.github.quanghung304.jobhunter.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class TrackingStatusMachineTest {

    /** Copied from data-model.md §2, independently of the implementation. */
    private static final Map<String, Set<String>> SPEC = Map.of(
            "NEW", Set.of("SHORTLISTED", "DISMISSED", "APPLIED"),
            "SHORTLISTED", Set.of("DISMISSED", "APPLIED", "NEW"),
            "DISMISSED", Set.of("NEW"),
            "APPLIED", Set.of("INTERVIEWING", "REJECTED", "WITHDRAWN"),
            "INTERVIEWING", Set.of("OFFER", "REJECTED", "WITHDRAWN"),
            "OFFER", Set.of(),
            "REJECTED", Set.of(),
            "WITHDRAWN", Set.of());

    /** Every (from, to) pair: 8 × 8 = 64 cases. */
    static Stream<Arguments> allPairs() {
        return Arrays.stream(TrackingStatus.values()).flatMap(from -> Arrays.stream(TrackingStatus.values())
                .map(to -> Arguments.of(from, to, SPEC.get(from.name()).contains(to.name()))));
    }

    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @MethodSource("allPairs")
    void followsTheSpecMatrix(TrackingStatus from, TrackingStatus to, boolean allowed) {
        assertThat(TrackingStatusMachine.canTransition(from, to)).isEqualTo(allowed);
        if (allowed) {
            assertThatCode(() -> TrackingStatusMachine.check(from, to)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> TrackingStatusMachine.check(from, to))
                    .isInstanceOfSatisfying(IllegalStatusTransitionException.class, e -> {
                        assertThat(e.code()).isEqualTo("ILLEGAL_STATUS_TRANSITION");
                        assertThat(e.httpStatus()).isEqualTo(409);
                        assertThat(e.from()).isEqualTo(from);
                        assertThat(e.to()).isEqualTo(to);
                    });
        }
    }

    @Test
    void specCoversEveryStatus() {
        assertThat(SPEC.keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(TrackingStatus.values()).map(Enum::name).toList());
    }

    @ParameterizedTest
    @EnumSource(TrackingStatus.class)
    void allowedTargetsMatchTheSpec(TrackingStatus from) {
        assertThat(TrackingStatusMachine.allowedTargets(from)).extracting(Enum::name)
                .containsExactlyInAnyOrderElementsOf(SPEC.get(from.name()));
        assertThat(TrackingStatusMachine.isTerminal(from)).isEqualTo(SPEC.get(from.name()).isEmpty());
    }

    @Test
    void allowedTargetsAreUnmodifiable() {
        var targets = TrackingStatusMachine.allowedTargets(TrackingStatus.NEW);

        assertThatThrownBy(() -> targets.add(TrackingStatus.OFFER)).isInstanceOf(UnsupportedOperationException.class);
    }
}
