package io.github.quanghung304.jobhunter.llm;

/** Why the model serving a purpose changed ({@code llm_assignment_event.reason}). */
public enum AssignmentChangeReason {
    USER,
    AUTO_FALLBACK,
    RESTORED
}
