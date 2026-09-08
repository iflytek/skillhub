package com.iflytek.skillhub.auth.operation;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdentityOperationSpringDataRepository extends JpaRepository<IdentityOperation, String> {

    Optional<IdentityOperation> findFirstByOrganizationIdAndConnectionIdAndOperationTypeOrderByStartedAtDescIdDesc(
            String organizationId,
            String connectionId,
            String operationType
    );
}
