package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * JPA repository for direct user-to-role assignments in the RBAC model.
 */
@Repository
public interface UserRoleBindingRepository extends JpaRepository<UserRoleBinding, Long> {
    List<UserRoleBinding> findByUserId(String userId);
    List<UserRoleBinding> findByUserIdIn(Collection<String> userIds);
    long deleteByUserId(String userId);
}
