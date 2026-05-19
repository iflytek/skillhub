package com.iflytek.skillhub.dto.cli;

public record CliSearchItemResponse(
        String namespace,
        String slug,
        String latestVersion,
        String summary
) {}
