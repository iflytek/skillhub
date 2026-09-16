package com.iflytek.skillhub.dto;

/**
 * Immutable member snapshot returned from a Suite draft or published version.
 * Live display metadata and browsing permission are viewer-specific; mutation responses therefore
 * keep them empty until the client fetches the detail projection.
 */
public record SkillSuiteMemberResponse(
        Long skillId,
        Long skillVersionId,
        String namespace,
        String slug,
        String displayName,
        String summary,
        String version,
        String fingerprint,
        int position,
        boolean entry,
        boolean browsable,
        String blockingReason
) {
}
