package com.iflytek.skillhub.dto;

import java.time.Instant;
import java.util.List;

/** Paginated owner workbench, including creation operations before a Suite exists. */
public record MySkillSuiteWorkspaceResponse(
        List<Item> items, long total, int page, int size,
        long attentionCount, boolean hasChangingOperations
) {
    public record Item(
            Long suiteId, String namespace, String slug, String displayName, String summary,
            String version, String suiteVersion, String state, Instant updatedAt,
            String operationId, String operationStatus, String failureCode
    ) {
    }
}
