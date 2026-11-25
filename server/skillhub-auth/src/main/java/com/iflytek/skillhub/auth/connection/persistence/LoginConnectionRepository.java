package com.iflytek.skillhub.auth.connection.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginConnectionRepository extends JpaRepository<LoginConnection, String> {

    Optional<LoginConnection> findByOrganizationIdAndId(String organizationId, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select connection from LoginConnection connection
            where connection.organizationId = :organizationId and connection.id = :id
            """)
    Optional<LoginConnection> lockByOrganizationIdAndId(@Param("organizationId") String organizationId,
                                                        @Param("id") String id);
}
