package io.github.quanghung304.jobhunter.source.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectorRunRepository extends JpaRepository<CollectorRun, Long> {

    Optional<CollectorRun> findFirstBySourceCodeOrderByStartedAtDesc(String sourceCode);
}
