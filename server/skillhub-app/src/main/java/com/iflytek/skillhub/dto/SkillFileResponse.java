package com.iflytek.skillhub.dto;

public record SkillFileResponse(
        Long id,
        String filePath,
        long fileSize,
        String contentType,
        String sha256
) {}
