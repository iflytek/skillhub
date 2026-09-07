package com.iflytek.skillhub.auth.connection.secret;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter that exposes no unscoped lookup and no raw Secret material. */
@Repository
public class JpaLoginConnectionSecretRepositoryAdapter
        implements LoginConnectionSecretRepository {

    private final LoginConnectionSecretSpringDataRepository delegate;

    public JpaLoginConnectionSecretRepositoryAdapter(
            LoginConnectionSecretSpringDataRepository delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional
    public List<LoginConnectionSecretVersion> findAllForUpdate(
            String scopeKey,
            String connectionId
    ) {
        return delegate.findAllForUpdate(
                requireText(scopeKey, "scopeKey"),
                requireText(connectionId, "connectionId")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginConnectionSecretVersion> findAll(
            String scopeKey,
            String connectionId,
            SecretPurpose purpose
    ) {
        return delegate.findAllByScopeKeyAndConnectionIdAndPurposeOrderByBindingVersionDesc(
                requireText(scopeKey, "scopeKey"),
                requireText(connectionId, "connectionId"),
                Objects.requireNonNull(purpose, "purpose").value()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginConnectionSecretVersion> findByReference(SecretReference reference) {
        SecretReference requiredReference = Objects.requireNonNull(reference, "reference");
        return delegate.findByScopeKeyAndConnectionIdAndPurposeAndBindingVersion(
                requiredReference.scopeKey(),
                requiredReference.connectionId(),
                requiredReference.purpose().value(),
                requiredReference.bindingVersion()
        );
    }

    @Override
    @Transactional
    public LoginConnectionSecretVersion saveAndFlush(
            LoginConnectionSecretVersion secretVersion
    ) {
        return delegate.saveAndFlush(Objects.requireNonNull(secretVersion, "secretVersion"));
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
