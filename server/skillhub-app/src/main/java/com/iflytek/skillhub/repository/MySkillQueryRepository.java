package com.iflytek.skillhub.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import java.util.List;

/**
 * Query-side repository for assembling the skill summary cards shown in
 * owner-facing and starred-skill lists.
 */
public interface MySkillQueryRepository {
    Page<Skill> findOwnedNamespaceSkills(String userId, String namespace, String keyword, Pageable page);

    List<SkillSummaryResponse> getSkillSummaries(List<Skill> skills, String currentUserId);

    org.springframework.data.domain.Page<Skill> findPendingSkills(String ownerId, String keyword,
            Long namespaceId, org.springframework.data.domain.Pageable pageable);

    java.util.Map<Long, SkillSummaryResponse.ManagementState> getManagementStates(List<Skill> skills, String ownerId);

    List<SkillSummaryResponse> getHiddenSkillSummaries(List<Skill> skills);
}
