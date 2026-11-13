package com.iflytek.skillhub.dto.cli;

import java.time.Instant;
import java.util.List;

public record CliWhoAmIResponse(String handle, String displayName, String email,
                                List<String> capabilities, boolean legacyCompat, Instant expiresAt) {}
