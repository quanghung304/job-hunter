package io.github.quanghung304.jobhunter.notify.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Notifications of a profile whose {@code job_ids} contain {@code jobId} (GIN index). */
    @Query(value = "select * from notification where profile_id = :profileId and job_ids @> array[:jobId]::bigint[]",
            nativeQuery = true)
    List<Notification> findByProfileIdContainingJob(@Param("profileId") String profileId, @Param("jobId") long jobId);
}
