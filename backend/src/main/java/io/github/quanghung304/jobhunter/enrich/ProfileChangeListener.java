package io.github.quanghung304.jobhunter.enrich;

import io.github.quanghung304.jobhunter.profile.ProfileChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * After the owner saved the profile (ADR-0012): runs the prefilter again and re-evaluates the
 * eligibility checks of analyzed jobs from their stored extraction. Java only, no LLM call; scores
 * and the owner's decisions are kept. Synchronous, so the save can answer with the counts.
 */
@Component
class ProfileChangeListener {

    private final AssessmentService assessments;

    ProfileChangeListener(AssessmentService assessments) {
        this.assessments = assessments;
    }

    @EventListener
    void onProfileChanged(ProfileChanged event) {
        Reassessment counts = assessments.reassessPrefilter(event.profile(), event.newHash());
        event.reassessed(new ProfileChanged.Reassessment(counts.jobs(), counts.prefilterChanged(),
                counts.checksUpdated()));
    }
}
