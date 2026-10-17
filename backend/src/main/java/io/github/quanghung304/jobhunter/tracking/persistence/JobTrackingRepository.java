package io.github.quanghung304.jobhunter.tracking.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobTrackingRepository extends JpaRepository<JobTracking, JobTrackingId> {
}
