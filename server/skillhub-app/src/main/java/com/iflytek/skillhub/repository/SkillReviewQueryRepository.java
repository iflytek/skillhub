package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.dto.SkillReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillReviewQueryRepository {
    Page<SkillReviewResponse> list(Long skillId, String viewerId, boolean includeHidden, Pageable pageable);
}
