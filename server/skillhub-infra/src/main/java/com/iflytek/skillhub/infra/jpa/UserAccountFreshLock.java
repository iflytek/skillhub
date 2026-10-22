package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.user.UserAccount;
import java.util.Optional;

public interface UserAccountFreshLock {
    Optional<UserAccount> findLockedFreshById(String id);
}
