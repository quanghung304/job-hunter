package io.github.quanghung304.jobhunter.profile;

/** Result of {@link RegionMatcher#isAccepted}; UNKNOWN when the job states no region. */
public enum RegionVerdict {
    OK,
    UNKNOWN,
    FAIL
}
