package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmAssignmentEventRepository extends JpaRepository<LlmAssignmentEvent, Long> {

    List<LlmAssignmentEvent> findByPurposeOrderByIdAsc(LlmPurpose purpose);
}
