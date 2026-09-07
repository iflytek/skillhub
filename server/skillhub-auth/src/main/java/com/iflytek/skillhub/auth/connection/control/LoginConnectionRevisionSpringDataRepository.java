package com.iflytek.skillhub.auth.connection.control;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal Spring Data delegate; callers use the connection-scoped repository port. */
interface LoginConnectionRevisionSpringDataRepository
        extends JpaRepository<StoredLoginConnectionRevision, String> {

    Optional<StoredLoginConnectionRevision> findByConnectionIdAndId(
            String connectionId,
            String id
    );
}
