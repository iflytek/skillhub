package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Grants or revokes team-only automatic publication for one namespace member.
 */
public record TeamAutoPublicationRequest(
        @NotNull(message = "{validation.member.teamAutoPublication.notNull}")
        Boolean granted
) {}
