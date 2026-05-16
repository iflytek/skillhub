package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.media.MediaAsset;
import com.iflytek.skillhub.domain.media.MediaAssetRepository;
import com.iflytek.skillhub.domain.media.MediaAssetRole;
import com.iflytek.skillhub.domain.media.MediaOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaAssetJpaRepository extends JpaRepository<MediaAsset, Long>, MediaAssetRepository {

    @Override
    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.ownerType = :ownerType AND m.ownerId = :ownerId
        ORDER BY m.sortOrder ASC, m.id ASC
    """)
    List<MediaAsset> findByOwner(@Param("ownerType") MediaOwnerType ownerType,
                                 @Param("ownerId") Long ownerId);

    @Override
    @Query("""
        SELECT m FROM MediaAsset m
        WHERE m.ownerType = :ownerType AND m.ownerId = :ownerId AND m.role = :role
        ORDER BY m.sortOrder ASC, m.id ASC
    """)
    List<MediaAsset> findByOwnerAndRoleOrdered(@Param("ownerType") MediaOwnerType ownerType,
                                               @Param("ownerId") Long ownerId,
                                               @Param("role") MediaAssetRole role);

    @Override
    default Optional<MediaAsset> findFirstByOwnerAndRole(MediaOwnerType ownerType, Long ownerId, MediaAssetRole role) {
        List<MediaAsset> hits = findByOwnerAndRoleOrdered(ownerType, ownerId, role);
        return hits.isEmpty() ? Optional.empty() : Optional.of(hits.get(0));
    }

    @Override
    List<MediaAsset> findBySha256(String sha256);
}
