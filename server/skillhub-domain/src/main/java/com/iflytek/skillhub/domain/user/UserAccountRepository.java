package com.iflytek.skillhub.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for user-account identity lookups and administrative searches.
 */
public interface UserAccountRepository {
    Optional<UserAccount> findById(String id);
    List<UserAccount> findByIdIn(List<String> ids);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    Page<UserAccount> search(String keyword, UserStatus status, Pageable pageable);
    UserAccount save(UserAccount user);
}
