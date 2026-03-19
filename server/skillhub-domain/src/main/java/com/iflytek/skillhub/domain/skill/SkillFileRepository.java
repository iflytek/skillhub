package com.iflytek.skillhub.domain.skill;

import java.util.List;

/**
 * Domain repository contract for files belonging to one published or draft skill version.
 */
public interface SkillFileRepository {
    List<SkillFile> findByVersionId(Long versionId);
    SkillFile save(SkillFile file);
    <S extends SkillFile> List<S> saveAll(Iterable<S> files);
    void deleteByVersionId(Long versionId);
}
