package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillVersionCompareHunkResponse(
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        List<SkillVersionCompareLineResponse> lines
) {}
