package com.iflytek.skillhub.domain.suite.bundle;

import java.util.Optional;

public interface SkillSuiteBundleExecutionOperationRepository {
    SkillSuiteBundleExecutionOperation save(SkillSuiteBundleExecutionOperation operation);
    void flush();
    Optional<SkillSuiteBundleExecutionOperation> findById(String operationId);
    Optional<SkillSuiteBundleExecutionOperation> findByActorIdAndClientRequestId(
            String actorId, String clientRequestId);
    Optional<SkillSuiteBundleExecutionOperation> findByPreviewToken(String previewToken);
}
