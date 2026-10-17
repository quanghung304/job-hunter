package io.github.quanghung304.jobhunter.source.persistence;

import io.github.quanghung304.jobhunter.source.CollectStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectorRunRepository extends JpaRepository<CollectorRun, Long> {

    Optional<CollectorRun> findFirstBySourceCodeOrderByStartedAtDesc(String sourceCode);

    Optional<CollectorRun> findFirstBySourceCodeAndStatusOrderByStartedAtDesc(String sourceCode, CollectStatus status);
}
