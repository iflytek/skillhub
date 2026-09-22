package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter that deliberately exposes no unscoped Organization connection lookup. */
@Repository
public class JpaLoginConnectionRepositoryAdapter implements LoginConnectionRepository {

    private final LoginConnectionSpringDataRepository delegate;

    public JpaLoginConnectionRepositoryAdapter(LoginConnectionSpringDataRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginConnection> findAllByOrganizationId(String organizationId) {
        return delegate.findAllByOrganizationIdOrderByCreatedAtDescIdDesc(
                requireText(organizationId, "organizationId")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginConnection> findByOrganizationIdAndId(String organizationId, String id) {
        return delegate.findByOrganizationIdAndId(
                requireText(organizationId, "organizationId"),
                requireText(id, "id")
        );
    }

    @Override
    @Transactional
    public Optional<LoginConnection> lockByOrganizationIdAndId(String organizationId, String id) {
        return delegate.lockByOrganizationIdAndId(
                requireText(organizationId, "organizationId"),
                requireText(id, "id")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LoginConnection> findByPublicHandle(ConnectionHandle handle) {
        return delegate.findByPublicHandle(Objects.requireNonNull(handle, "handle").value());
    }

    @Override
    @Transactional
    public LoginConnection save(LoginConnection connection) {
        return delegate.saveAndFlush(Objects.requireNonNull(connection, "connection"));
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
