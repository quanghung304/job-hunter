package io.github.quanghung304.jobhunter.llm.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelStateRepository extends JpaRepository<LlmModelState, String> {
}
