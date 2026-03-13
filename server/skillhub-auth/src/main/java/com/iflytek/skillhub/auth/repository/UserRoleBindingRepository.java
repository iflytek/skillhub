package com.iflytek.skillhub.auth.repository;

import com.iflytek.skillhub.auth.entity.UserRoleBinding;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserRoleBindingRepository extends JpaRepository<UserRoleBinding, Long> {
    List<UserRoleBinding> findByUserId(String userId);
    List<UserRoleBinding> findByUserIdIn(Collection<String> userIds);
    long deleteByUserId(String userId);
}
