package com.iflytek.skillhub.auth.connection.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginConnectionRevisionRepository extends JpaRepository<LoginConnectionRevision, String> {

    Optional<LoginConnectionRevision> findFirstByConnectionIdOrderByRevisionDesc(String connectionId);

    Optional<LoginConnectionRevision> findByConnectionIdAndId(String connectionId, String id);
}
