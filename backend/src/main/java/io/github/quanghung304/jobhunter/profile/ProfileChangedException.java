package io.github.quanghung304.jobhunter.profile;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** The profile was saved or edited since the client loaded it ({@code baseHash} is stale); 409. */
public class ProfileChangedException extends JobHunterException {

    public static final String CODE = "PROFILE_CHANGED";

    public ProfileChangedException(String message) {
        super(CODE, message);
    }

    @Override
    public int httpStatus() {
        return 409;
    }
}
