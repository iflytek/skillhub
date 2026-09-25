package com.iflytek.skillhub.dto;

import java.util.Set;

/** Request body for submitting a validated draft into the publish pipeline. */
public record SubmitDraftRequest(
        String visibility,
        Set<String> platformRoles
) {
}
