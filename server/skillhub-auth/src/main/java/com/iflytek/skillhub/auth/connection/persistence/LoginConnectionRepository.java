package com.iflytek.skillhub.auth.connection.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginConnectionRepository extends JpaRepository<LoginConnection, String> {

    List<LoginConnection> findByOrganizationIdAndStatusOrderByDisplayNameAsc(
            String organizationId, LoginConnectionStatus status);

    Optional<LoginConnection> findByOrganizationIdAndId(String organizationId, String id);

    Optional<LoginConnection> findByPublicHandle(String publicHandle);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select connection from LoginConnection connection
            where connection.organizationId = :organizationId and connection.id = :id
            """)
    Optional<LoginConnection> lockByOrganizationIdAndId(@Param("organizationId") String organizationId,
                                                        @Param("id") String id);
}
