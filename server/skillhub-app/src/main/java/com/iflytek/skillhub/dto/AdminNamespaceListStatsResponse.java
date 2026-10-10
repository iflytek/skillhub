package com.iflytek.skillhub.dto;

public record AdminNamespaceListStatsResponse(
        long total,
        long active,
        long frozen,
        long archived
) {}
