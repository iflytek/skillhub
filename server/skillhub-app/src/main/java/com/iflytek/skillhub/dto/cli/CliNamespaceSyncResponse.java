package com.iflytek.skillhub.dto.cli;

import java.util.List;

/**
 * Cursor-shaped response consumed by the CLI namespace workspace synchronizer.
 */
public record CliNamespaceSyncResponse(
        List<CliNamespaceSyncItemResponse> items,
        String nextCursor
) {}
