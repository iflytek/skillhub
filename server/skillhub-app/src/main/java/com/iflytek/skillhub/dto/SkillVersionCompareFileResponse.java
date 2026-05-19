package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillVersionCompareFileResponse(
        String path,
        String changeType,
        Long oldSize,
        Long newSize,
        boolean binary,
        boolean truncated,
        List<SkillVersionCompareHunkResponse> hunks
) {}
