package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iflytek.skillhub.domain.skill.validation.SkillExamplesSummary.Status;

/** Package defaults only: preview never creates a skill or changes its files. */
public record SkillPackagePreviewResponse(String name, String displayName, String description, String shortDescription,
                                          String overview, String version,
                                          @JsonInclude(JsonInclude.Include.NON_NULL) Status examplesStatus,
                                          @JsonInclude(JsonInclude.Include.NON_NULL) Integer examplesCount) {
    public SkillPackagePreviewResponse(String name, String displayName, String description, String version) {
        this(name, displayName, description, description, null, version, null, null);
    }
}
