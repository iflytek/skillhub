package com.iflytek.skillhub.auth.operation;

import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaIdentityOperationRepositoryAdapter implements IdentityOperationRepository {

    private final IdentityOperationSpringDataRepository delegate;

    public JpaIdentityOperationRepositoryAdapter(IdentityOperationSpringDataRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public IdentityOperation save(IdentityOperation operation) {
        return delegate.saveAndFlush(Objects.requireNonNull(operation, "operation"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityOperation> findLatestLoginConnectionTest(
            String organizationId,
            String connectionId
    ) {
        return delegate.findFirstByOrganizationIdAndConnectionIdAndOperationTypeOrderByStartedAtDescIdDesc(
                requireText(organizationId, "organizationId"),
                requireText(connectionId, "connectionId"),
                IdentityOperation.LOGIN_CONNECTION_TEST
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
