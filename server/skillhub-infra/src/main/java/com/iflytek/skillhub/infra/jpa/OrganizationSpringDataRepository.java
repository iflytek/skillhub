package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.Organization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Internal Spring Data delegate; domain callers use {@link JpaOrganizationRepositoryAdapter}. */
interface OrganizationSpringDataRepository extends JpaRepository<Organization, String> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select organization from Organization organization where organization.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") String id);

    Optional<Organization> findBySlug(String slug);
}
