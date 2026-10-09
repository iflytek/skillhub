package com.iflytek.skillhub.domain.review;

public record PromotionState(String requestKind, Long targetSkillId,
                             String targetCurrentVersion, Long pendingPromotionId) {}
