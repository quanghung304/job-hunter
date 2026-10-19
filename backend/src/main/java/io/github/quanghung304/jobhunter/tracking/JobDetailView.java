package io.github.quanghung304.jobhunter.tracking;

import io.github.quanghung304.jobhunter.enrich.AssessmentView;
import io.github.quanghung304.jobhunter.enrich.JobAttributes;
import io.github.quanghung304.jobhunter.ingest.JobView;
import io.github.quanghung304.jobhunter.ingest.PostingView;
import java.util.List;
import java.util.Set;

/**
 * Everything the job detail page needs for one profile (api-spec {@code JobDetail}).
 *
 * @param attributes         the OK extraction for the current prompt version, null if none
 * @param assessment         the profile's assessment row, null if the job was never prefiltered
 * @param notes              null when empty
 * @param allowedTransitions statuses reachable from {@code summary.status()}
 */
public record JobDetailView(
        JobBoardRow summary,
        JobView job,
        JobAttributes attributes,
        AssessmentView assessment,
        List<PostingView> postings,
        String notes,
        List<StatusChange> history,
        Set<TrackingStatus> allowedTransitions) {

    public JobDetailView {
        postings = List.copyOf(postings);
        history = List.copyOf(history);
        allowedTransitions = Set.copyOf(allowedTransitions);
    }
}
