package com.iflytek.skillhub.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NotificationEmailContactResponse(
        @Schema(nullable = true) String email,
        boolean verified,
        boolean available,
        String locale,
        @Schema(nullable = true) Instant resendAvailableAt,
        @Schema(nullable = true) String reason) {}
