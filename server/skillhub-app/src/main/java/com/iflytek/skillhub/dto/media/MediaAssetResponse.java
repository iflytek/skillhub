package com.iflytek.skillhub.dto.media;

import com.iflytek.skillhub.domain.media.MediaAsset;
import com.iflytek.skillhub.domain.media.MediaAssetRole;
import com.iflytek.skillhub.domain.media.MediaOwnerType;
import com.iflytek.skillhub.domain.media.MediaType;

import java.time.Instant;

public record MediaAssetResponse(
        Long id,
        MediaOwnerType ownerType,
        Long ownerId,
        MediaType mediaType,
        MediaAssetRole role,
        String url,
        String contentType,
        long sizeBytes,
        String altText,
        Instant createdAt
) {
    public static MediaAssetResponse from(MediaAsset asset) {
        return new MediaAssetResponse(
                asset.getId(), asset.getOwnerType(), asset.getOwnerId(),
                asset.getMediaType(), asset.getRole(),
                "/api/v1/media/" + asset.getId(),
                asset.getContentType(), asset.getSizeBytes(),
                asset.getAltText(), asset.getCreatedAt()
        );
    }
}
