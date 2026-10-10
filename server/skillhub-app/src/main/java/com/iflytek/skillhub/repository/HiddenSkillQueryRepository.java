package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.skill.Skill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Query-side access for the platform hidden-skill governance list. */
public interface HiddenSkillQueryRepository {
    Page<Skill> search(String keyword, Long namespaceId, Pageable pageable);
}
