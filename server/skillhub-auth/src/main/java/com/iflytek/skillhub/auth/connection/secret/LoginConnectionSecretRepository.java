package com.iflytek.skillhub.auth.connection.secret;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoginConnectionSecretRepository
        extends JpaRepository<LoginConnectionSecretVersion, LoginConnectionSecretVersion.Id> {

    @Query("""
            select coalesce(max(secret.id.bindingVersion), 0)
            from LoginConnectionSecretVersion secret
            where secret.id.connectionId = :connectionId and secret.organizationId = :organizationId
            """)
    long latestVersion(@Param("organizationId") String organizationId,
                       @Param("connectionId") String connectionId);
}
