package io.github.quanghung304.jobhunter.tracking;

import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.APPLIED;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.DISMISSED;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.INTERVIEWING;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.NEW;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.OFFER;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.REJECTED;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.SHORTLISTED;
import static io.github.quanghung304.jobhunter.tracking.TrackingStatus.WITHDRAWN;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Allowed tracking status transitions (data-model.md §2). OFFER, REJECTED and WITHDRAWN are
 * terminal; staying in the same status is not a transition.
 */
public final class TrackingStatusMachine {

    private static final Map<TrackingStatus, Set<TrackingStatus>> ALLOWED = new EnumMap<>(Map.of(
            NEW, EnumSet.of(SHORTLISTED, DISMISSED, APPLIED),
            SHORTLISTED, EnumSet.of(DISMISSED, APPLIED, NEW),
            DISMISSED, EnumSet.of(NEW),
            APPLIED, EnumSet.of(INTERVIEWING, REJECTED, WITHDRAWN),
            INTERVIEWING, EnumSet.of(OFFER, REJECTED, WITHDRAWN),
            OFFER, EnumSet.noneOf(TrackingStatus.class),
            REJECTED, EnumSet.noneOf(TrackingStatus.class),
            WITHDRAWN, EnumSet.noneOf(TrackingStatus.class)));

    private TrackingStatusMachine() {
    }

    /** Statuses reachable from {@code from} in one step; empty for terminal statuses. */
    public static Set<TrackingStatus> allowedTargets(TrackingStatus from) {
        return Set.copyOf(ALLOWED.get(Objects.requireNonNull(from, "from")));
    }

    public static boolean canTransition(TrackingStatus from, TrackingStatus to) {
        return allowedTargets(from).contains(Objects.requireNonNull(to, "to"));
    }

    /** @throws IllegalStatusTransitionException if {@code from → to} is not allowed */
    public static void check(TrackingStatus from, TrackingStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStatusTransitionException(from, to);
        }
    }

    public static boolean isTerminal(TrackingStatus status) {
        return allowedTargets(status).isEmpty();
    }
}
