package com.iflytek.skillhub.auth.connection.control;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal Spring Data delegate; callers use the tenant-scoped repository port. */
interface LoginConnectionSpringDataRepository extends JpaRepository<LoginConnection, String> {

    Optional<LoginConnection> findByOrganizationIdAndId(String organizationId, String id);

    Optional<LoginConnection> findByPublicHandle(String publicHandle);
}
