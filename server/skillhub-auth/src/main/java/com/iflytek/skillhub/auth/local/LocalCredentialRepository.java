package com.iflytek.skillhub.auth.local;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalCredentialRepository extends JpaRepository<LocalCredential, Long> {

    Optional<LocalCredential> findByUsernameIgnoreCase(String username);

    Optional<LocalCredential> findByUserId(String userId);

    boolean existsByUsernameIgnoreCase(String username);
}
