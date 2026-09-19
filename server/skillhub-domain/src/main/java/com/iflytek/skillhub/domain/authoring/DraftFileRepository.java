package com.iflytek.skillhub.domain.authoring;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for draft files.
 */
public interface DraftFileRepository {

    DraftFile save(DraftFile file);

    List<DraftFile> findByDraftIdOrderByFilePath(Long draftId);

    Optional<DraftFile> findByDraftIdAndFilePath(Long draftId, String filePath);

    List<DraftFile> findByDraftIdAndFilePathIn(Long draftId, List<String> filePaths);

    void delete(DraftFile file);

    void deleteByDraftId(Long draftId);

    long countByDraftId(Long draftId);

    long sumSizeByDraftId(Long draftId);
}
