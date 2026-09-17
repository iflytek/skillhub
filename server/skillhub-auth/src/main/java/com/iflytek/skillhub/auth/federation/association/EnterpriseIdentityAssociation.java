package com.iflytek.skillhub.auth.federation.association;

import com.iflytek.skillhub.auth.federation.core.IdentityCorrelationStage;
import java.util.Objects;

/** Transactionally consistent result consumed by later guard/session orchestration. */
public record EnterpriseIdentityAssociation(
        String externalIdentityId,
        String userId,
        String membershipId,
        IdentityCorrelationStage stage,
        boolean accountCreated
) {
    public EnterpriseIdentityAssociation {
        Objects.requireNonNull(externalIdentityId, "externalIdentityId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
    }
}
