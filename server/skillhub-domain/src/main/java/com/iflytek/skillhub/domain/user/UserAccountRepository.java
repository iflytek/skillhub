package com.iflytek.skillhub.domain.user;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findById(String id);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    UserAccount save(UserAccount user);
}
