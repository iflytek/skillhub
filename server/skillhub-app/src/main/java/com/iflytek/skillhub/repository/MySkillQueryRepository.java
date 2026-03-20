package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import java.util.List;

/**
 * Query-side repository for assembling the skill summary cards shown in
 * owner-facing and starred-skill lists.
 */
public interface MySkillQueryRepository {
    List<SkillSummaryResponse> getSkillSummaries(List<Skill> skills, String currentUserId);
}
