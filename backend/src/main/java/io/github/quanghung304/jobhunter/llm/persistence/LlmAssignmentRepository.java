package io.github.quanghung304.jobhunter.llm.persistence;

import io.github.quanghung304.jobhunter.llm.LlmPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmAssignmentRepository extends JpaRepository<LlmAssignment, LlmPurpose> {
}
