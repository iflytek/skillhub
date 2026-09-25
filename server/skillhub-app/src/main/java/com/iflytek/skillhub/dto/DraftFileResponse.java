package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.authoring.DraftFile;
import java.time.Instant;

/** Draft file metadata (content is fetched separately). */
public record DraftFileResponse(
        Long id,
        String path,
        String sha256,
        Long size,
        String contentType,
        Instant updatedAt
) {

    public static DraftFileResponse from(DraftFile file) {
        return new DraftFileResponse(
                file.getId(),
                file.getFilePath(),
                file.getSha256(),
                file.getSize(),
                file.getContentType(),
                file.getUpdatedAt());
    }
}
