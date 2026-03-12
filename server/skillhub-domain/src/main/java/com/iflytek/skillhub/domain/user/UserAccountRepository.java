package com.iflytek.skillhub.domain.user;

import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findById(String id);
    UserAccount save(UserAccount user);
}
