package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.authoring.DraftFile;
import com.iflytek.skillhub.domain.authoring.DraftFileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * JPA-backed repository for draft files.
 */
@Repository
public interface DraftFileJpaRepository extends JpaRepository<DraftFile, Long>, DraftFileRepository {

    List<DraftFile> findByDraftIdOrderByFilePath(Long draftId);

    Optional<DraftFile> findByDraftIdAndFilePath(Long draftId, String filePath);

    List<DraftFile> findByDraftIdAndFilePathIn(Long draftId, List<String> filePaths);

    long countByDraftId(Long draftId);

    @Query("SELECT COALESCE(SUM(file.size), 0) FROM DraftFile file WHERE file.draftId = :draftId")
    long sumSizeByDraftId(@Param("draftId") Long draftId);

    void deleteByDraftId(Long draftId);
}
