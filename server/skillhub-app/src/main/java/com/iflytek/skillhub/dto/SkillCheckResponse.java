package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillCheckResponse(
    boolean valid,
    List<String> errors,
    int fileCount,
    long totalSize
) {}
