package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.bundle.SkillBundleReviewTask;
import com.iflytek.skillhub.domain.bundle.SkillBundleReviewTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillBundleReviewTaskJpaRepository extends JpaRepository<SkillBundleReviewTask, Long>,
                                                            SkillBundleReviewTaskRepository {

    @Override
    Optional<SkillBundleReviewTask> findByBundleVersionId(Long bundleVersionId);

    @Override
    Page<SkillBundleReviewTask> findByStatusAndNamespaceId(String status, Long namespaceId, Pageable pageable);

    @Override
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE SkillBundleReviewTask t
        SET t.status = :status,
            t.reviewedBy = :reviewedBy,
            t.reviewComment = :reviewComment,
            t.reviewedAt = CURRENT_TIMESTAMP,
            t.version = t.version + 1
        WHERE t.id = :id AND t.version = :expectedVersion
    """)
    int updateStatusWithVersion(@Param("id") Long id,
                                @Param("status") String newStatus,
                                @Param("reviewedBy") String reviewedBy,
                                @Param("reviewComment") String reviewComment,
                                @Param("expectedVersion") Integer expectedVersion);
}
