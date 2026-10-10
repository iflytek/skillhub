package com.iflytek.skillhub.dto.cli;

public record CliDeleteResponse(
        boolean ok,
        String scope,
        String action,
        String namespace,
        String slug
) {}
