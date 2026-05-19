package com.iflytek.skillhub.domain.social;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SkillSubscriptionRepository {
    SkillSubscription save(SkillSubscription subscription);
    Optional<SkillSubscription> findBySkillIdAndUserId(Long skillId, String userId);
    void delete(SkillSubscription subscription);
    void deleteBySkillId(Long skillId);
    Page<SkillSubscription> findByUserId(String userId, Pageable pageable);
    List<SkillSubscription> findAllBySkillId(Long skillId);
    long countBySkillId(Long skillId);
}
