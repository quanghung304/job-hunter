package io.github.quanghung304.jobhunter.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.quanghung304.jobhunter.PostgresJpaTest;
import io.github.quanghung304.jobhunter.llm.client.FakeLlmClient;
import io.github.quanghung304.jobhunter.llm.persistence.LlmAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/** ADR-0009 with paid calls off: switching to PAID, turning on paid fallback or testing a PAID model is refused. */
@PostgresJpaTest
@Import({LlmRouterTestConfig.class, LlmAlertService.class, LlmAdminService.class, LlmUsageService.class})
@TestPropertySource(properties = "test.llm.allow-paid=false")
class PaidSwitchConsentIT {

    @Autowired
    LlmAdminService admin;

    @Autowired
    LlmAssignmentRepository assignments;

    @Autowired
    FakeLlmClient fake;

    @Test
    void paidActionsAreRefusedEvenWhenConfirmed() {
        fake.reset();

        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.SCORE, "paid", null, null, null, true))
                .isInstanceOf(PaidCallsDisabledException.class);
        assertThatThrownBy(() -> admin.switchModel(LlmPurpose.SCORE, "primary", null, true, true, true))
                .isInstanceOf(PaidCallsDisabledException.class);
        assertThatThrownBy(() -> admin.test("paid", true)).isInstanceOf(PaidCallsDisabledException.class);

        assertThat(fake.requests()).isEmpty();
        assertThat(assignments.findById(LlmPurpose.SCORE)).hasValueSatisfying(row -> {
            assertThat(row.getModelId()).isEqualTo("primary");
            assertThat(row.isAllowPaid()).isFalse();
        });
        assertThat(admin.overview().paidCallsAllowed()).isFalse();
    }
}
