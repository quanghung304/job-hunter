package io.github.quanghung304.jobhunter.profile;

import io.github.quanghung304.jobhunter.common.JobHunterException;
import java.util.List;

/** A profile sent from the Profile page breaks the rules of config-spec §1; 400 with every problem by path. */
public class ProfileValidationException extends JobHunterException {

    public static final String CODE = "VALIDATION_FAILED";

    private final transient List<ProfileProblem> problems;

    public ProfileValidationException(List<ProfileProblem> problems) {
        super(CODE, problems.size() == 1 ? "The profile has 1 problem" : "The profile has " + problems.size()
                + " problems");
        this.problems = List.copyOf(problems);
    }

    public List<ProfileProblem> problems() {
        return problems;
    }
}
