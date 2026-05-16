package com.iflytek.skillhub.domain.media;

import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for media assets.
 */
public interface MediaAssetRepository {
    MediaAsset save(MediaAsset asset);
    Optional<MediaAsset> findById(Long id);
    List<MediaAsset> findByOwner(MediaOwnerType ownerType, Long ownerId);
    Optional<MediaAsset> findFirstByOwnerAndRole(MediaOwnerType ownerType, Long ownerId, MediaAssetRole role);
    List<MediaAsset> findBySha256(String sha256);
    void delete(MediaAsset asset);
}
