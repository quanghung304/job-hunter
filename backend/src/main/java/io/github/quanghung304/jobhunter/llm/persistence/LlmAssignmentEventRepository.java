package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmAssignmentEventRepository extends JpaRepository<LlmAssignmentEvent, Long> {

    List<LlmAssignmentEvent> findByPurposeOrderByIdAsc(LlmPurpose purpose);

    /** The newest switch of a purpose. */
    Optional<LlmAssignmentEvent> findFirstByPurposeOrderByCreatedAtDescIdDesc(LlmPurpose purpose);

    /** The last ten switches of all purposes, newest first (admin "Switch history"). */
    List<LlmAssignmentEvent> findTop10ByOrderByCreatedAtDescIdDesc();
}
