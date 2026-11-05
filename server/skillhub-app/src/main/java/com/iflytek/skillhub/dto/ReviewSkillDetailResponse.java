package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ReviewSkillDetailResponse(
        SkillDetailResponse skill,
        List<SkillVersionResponse> versions,
        List<SkillFileResponse> files,
        String documentationPath,
        String documentationContent,
        String downloadUrl,
        String activeVersion,
        AvatarChange avatarChange,
        VisibilityChange visibilityChange,
        Presentation presentation,
        Comparison comparison
) {
    public ReviewSkillDetailResponse(SkillDetailResponse skill, List<SkillVersionResponse> versions,
            List<SkillFileResponse> files, String documentationPath, String documentationContent,
            String downloadUrl, String activeVersion, AvatarChange avatarChange, VisibilityChange visibilityChange) {
        this(skill, versions, files, documentationPath, documentationContent, downloadUrl, activeVersion,
                avatarChange, visibilityChange, null, null);
    }
    public ReviewSkillDetailResponse(SkillDetailResponse skill, List<SkillVersionResponse> versions,
            List<SkillFileResponse> files, String documentationPath, String documentationContent,
            String downloadUrl, String activeVersion, AvatarChange avatarChange) {
        this(skill, versions, files, documentationPath, documentationContent, downloadUrl, activeVersion, avatarChange, null);
    }
    @Schema(name = "ReviewPresentation")
    public record Presentation(String displayName, String summary, String overview, String overviewSource) {}
    @Schema(name = "ReviewFileChange")
    public record FileChange(String path, String changeType) {}
    @Schema(name = "ReviewComparison")
    public record Comparison(String baseVersion, Presentation baseline, Presentation current, List<FileChange> files) {}
    public record VisibilityChange(String previousVisibility, String requestedVisibility,
            String scanStatus, boolean readyForApproval) {}
    public record AvatarChange(Long candidateId, String currentAvatarUrl, String candidatePreviewUrl,
            String machineStatus, String reviewStatus, boolean readyForApproval, boolean removal) {}
    public ReviewSkillDetailResponse(SkillDetailResponse skill, List<SkillVersionResponse> versions,
            List<SkillFileResponse> files, String documentationPath, String documentationContent,
            String downloadUrl, String activeVersion) {
        this(skill, versions, files, documentationPath, documentationContent, downloadUrl, activeVersion, null);
    }
}
