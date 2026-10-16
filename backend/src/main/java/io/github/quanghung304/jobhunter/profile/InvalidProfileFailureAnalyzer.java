package io.github.quanghung304.jobhunter.profile;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/** Turns an invalid profile into a short "APPLICATION FAILED TO START" report. */
class InvalidProfileFailureAnalyzer extends AbstractFailureAnalyzer<InvalidProfileException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidProfileException cause) {
        return new FailureAnalysis(
                cause.getMessage(),
                "Fix the fields above in " + cause.path().toAbsolutePath().normalize()
                        + " (template: config/profile.example.yaml, reference: docs/architecture/config-spec.md §1),"
                        + " or point app.profile.path to another file.",
                cause);
    }
}
