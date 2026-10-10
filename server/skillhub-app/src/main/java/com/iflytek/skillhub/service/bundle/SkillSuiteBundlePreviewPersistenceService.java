package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps PreviewSession persistence atomic without holding a transaction during ZIP processing. */
@Service
public class SkillSuiteBundlePreviewPersistenceService {

    private final SkillSuiteBundlePreviewSessionRepository previewRepository;

    public SkillSuiteBundlePreviewPersistenceService(
            SkillSuiteBundlePreviewSessionRepository previewRepository
    ) {
        this.previewRepository = previewRepository;
    }

    @Transactional
    public void save(SkillSuiteBundlePreviewSession preview) {
        previewRepository.save(preview);
        // Surface serialization and constraint failures before staged-object compensation runs.
        previewRepository.flush();
    }
}
