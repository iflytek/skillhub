package com.iflytek.skillhub.auth.connection.control;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal Spring Data delegate; callers use the tenant-scoped repository port. */
interface LoginConnectionSpringDataRepository extends JpaRepository<LoginConnection, String> {

    List<LoginConnection> findAllByOrganizationIdOrderByCreatedAtDescIdDesc(
            String organizationId
    );

    Optional<LoginConnection> findByOrganizationIdAndId(String organizationId, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select connection
            from LoginConnection connection
            where connection.organizationId = :organizationId and connection.id = :id
            """)
    Optional<LoginConnection> lockByOrganizationIdAndId(
            @Param("organizationId") String organizationId,
            @Param("id") String id
    );

    Optional<LoginConnection> findByPublicHandle(String publicHandle);
}
