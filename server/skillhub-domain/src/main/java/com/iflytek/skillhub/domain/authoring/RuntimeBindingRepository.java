package com.iflytek.skillhub.domain.authoring;

import java.util.Optional;

/**
 * Domain repository contract for per-draft runtime bindings.
 */
public interface RuntimeBindingRepository {

    RuntimeBinding save(RuntimeBinding binding);

    Optional<RuntimeBinding> findByDraftId(Long draftId);

    void deleteByDraftId(Long draftId);
}
