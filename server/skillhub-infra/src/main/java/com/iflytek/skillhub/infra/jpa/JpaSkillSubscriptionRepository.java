package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillSubscription;
import com.iflytek.skillhub.domain.social.SkillSubscriptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JpaSkillSubscriptionRepository extends JpaRepository<SkillSubscription, Long>, SkillSubscriptionRepository {
    Optional<SkillSubscription> findBySkillIdAndUserId(Long skillId, String userId);
    void deleteBySkillId(Long skillId);
    Page<SkillSubscription> findByUserId(String userId, Pageable pageable);
    List<SkillSubscription> findAllBySkillId(Long skillId);
    long countBySkillId(Long skillId);
}
