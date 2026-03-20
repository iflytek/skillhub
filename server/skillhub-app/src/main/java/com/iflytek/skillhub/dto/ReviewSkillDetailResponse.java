package com.iflytek.skillhub.dto;

import java.util.List;

public record ReviewSkillDetailResponse(
        SkillDetailResponse skill,
        List<SkillVersionResponse> versions,
        List<SkillFileResponse> files,
        String documentationPath,
        String documentationContent,
        String downloadUrl,
        String activeVersion
) {}
