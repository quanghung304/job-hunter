package io.github.quanghung304.jobhunter.enrich;

/** Prefilter reject reasons (scoring-spec.md §2), in rule order; stored by name. */
public enum PrefilterReason {
    TOO_OLD,
    EXCLUDED_COMPANY,
    TITLE_EXCLUDED,
    NOT_RELEVANT,
    ONSITE_SOURCE_HINT
}
