package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferOwnershipRequest(
        @NotBlank(message = "{validation.transferOwnership.newOwnerId.notBlank}")
        String newOwnerId
) {}
