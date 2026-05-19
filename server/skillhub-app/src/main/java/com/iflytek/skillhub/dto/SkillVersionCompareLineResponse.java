package com.iflytek.skillhub.dto;

public record SkillVersionCompareLineResponse(
        String type,
        String content,
        Integer oldLineNumber,
        Integer newLineNumber
) {}
