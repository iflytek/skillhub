package com.iflytek.skillhub.auth.session;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentitySessionOriginRepository extends JpaRepository<IdentitySessionOrigin, String> {
    Optional<IdentitySessionOrigin> findBySessionKeyHash(String sessionKeyHash);
}
