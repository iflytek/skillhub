package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

interface DirectoryConnectionSpringDataRepository extends JpaRepository<DirectoryConnection, String> {

    Optional<DirectoryConnection> findByOrganizationIdAndId(String organizationId, String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DirectoryConnection c where c.organizationId = :organizationId and c.id = :id")
    Optional<DirectoryConnection> findByOrganizationIdAndIdForUpdate(String organizationId, String id);

    List<DirectoryConnection> findByOrganizationIdOrderByCreatedAtAscIdAsc(String organizationId);
}
