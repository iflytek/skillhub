package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull(message = "{validation.transferOwnership.newOwner.notNull}")
        String newOwnerId
) {}