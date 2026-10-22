package io.github.quanghung304.jobhunter.profile;

import io.github.quanghung304.jobhunter.common.JobHunterException;

/** The profile {@code id} keys rows in the database and cannot be changed from the app (ADR-0012); 400. */
public class ProfileIdImmutableException extends JobHunterException {

    public static final String CODE = "PROFILE_ID_IMMUTABLE";

    public ProfileIdImmutableException(String currentId, String requestedId) {
        super(CODE, "The profile id cannot be changed from the app (current '" + currentId + "', sent '"
                + requestedId + "')");
    }
}
