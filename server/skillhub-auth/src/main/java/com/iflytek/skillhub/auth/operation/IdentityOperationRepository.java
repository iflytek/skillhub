package com.iflytek.skillhub.auth.operation;

import java.util.Optional;

public interface IdentityOperationRepository {

    IdentityOperation save(IdentityOperation operation);

    Optional<IdentityOperation> findLatestLoginConnectionTest(
            String organizationId,
            String connectionId
    );
}
