package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillVersionCompareResponse(
        String from,
        String to,
        SkillVersionCompareSummaryResponse summary,
        List<SkillVersionCompareFileResponse> files
) {
    public record SkillVersionCompareSummaryResponse(
            int totalFiles,
            int addedFiles,
            int modifiedFiles,
            int removedFiles,
            int addedLines,
            int removedLines
    ) {}
}
