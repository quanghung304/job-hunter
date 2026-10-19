package io.github.quanghung304.jobhunter.tracking.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTrackingEventRepository extends JpaRepository<JobTrackingEvent, Long> {

    List<JobTrackingEvent> findByJobIdAndProfileIdOrderByCreatedAtAscIdAsc(Long jobId, String profileId);
}
