package com.iflytek.skillhub.auth.connection.secret;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal Spring Data delegate for encrypted Login Connection Secret rows. */
interface LoginConnectionSecretSpringDataRepository
        extends JpaRepository<LoginConnectionSecretVersion, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select secretVersion
            from LoginConnectionSecretVersion secretVersion
            where secretVersion.scopeKey = :scopeKey
              and secretVersion.connectionId = :connectionId
            order by secretVersion.bindingVersion desc
            """)
    List<LoginConnectionSecretVersion> findAllForUpdate(
            @Param("scopeKey") String scopeKey,
            @Param("connectionId") String connectionId
    );

    List<LoginConnectionSecretVersion>
            findAllByScopeKeyAndConnectionIdAndPurposeOrderByBindingVersionDesc(
                    String scopeKey,
                    String connectionId,
                    String purpose
            );

    Optional<LoginConnectionSecretVersion>
            findByScopeKeyAndConnectionIdAndPurposeAndBindingVersion(
                    String scopeKey,
                    String connectionId,
                    String purpose,
                    long bindingVersion
            );
}
