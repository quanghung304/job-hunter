package io.github.quanghung304.jobhunter.notify.persistence;

import io.github.quanghung304.jobhunter.notify.NotificationKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Notifications of a profile whose {@code job_ids} contain {@code jobId} (GIN index). */
    @Query(value = "select * from notification where profile_id = :profileId and job_ids @> array[:jobId]::bigint[]",
            nativeQuery = true)
    List<Notification> findByProfileIdContainingJob(@Param("profileId") String profileId, @Param("jobId") long jobId);

    /** Whether an alert with this dedupe key was sent successfully. */
    boolean existsByRefAndSuccessTrue(String ref);

    /** The last successful notification of this kind for the profile. */
    Optional<Notification> findFirstByProfileIdAndKindAndSuccessTrueOrderBySentAtDesc(String profileId,
            NotificationKind kind);

    /** Every job id of a successful notification of this kind for the profile. */
    @Query(value = """
            select distinct unnest(job_ids) from notification
            where profile_id = :profileId and kind = :kind and success""", nativeQuery = true)
    List<Long> findSentJobIds(@Param("profileId") String profileId, @Param("kind") String kind);
}
