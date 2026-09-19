package com.iflytek.skillhub.dto;

/** Draft file content, always delivered as text (draft files are text formats). */
public record DraftFileContentResponse(
        String path,
        String sha256,
        Long size,
        String contentType,
        String content
) {
}
