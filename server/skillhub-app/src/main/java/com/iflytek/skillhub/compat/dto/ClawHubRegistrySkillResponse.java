package com.iflytek.skillhub.compat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ClawHubRegistrySkillResponse(
        ClawHubRegistrySkill skill,
        ClawHubRegistrySkillVersion latestVersion,
        ClawHubRegistryOwner owner,
        ClawHubRegistryModeration moderation
) {}
