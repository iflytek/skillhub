package com.iflytek.skillhub.auth.connection.control;

import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for immutable revisions scoped through their owning connection. */
@Repository
public class JpaLoginConnectionRevisionRepositoryAdapter
        implements LoginConnectionRevisionRepository {

    private final LoginConnectionRevisionSpringDataRepository delegate;

    public JpaLoginConnectionRevisionRepositoryAdapter(
            LoginConnectionRevisionSpringDataRepository delegate
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredLoginConnectionRevision> findLatestByConnectionId(String connectionId) {
        return delegate.findFirstByConnectionIdOrderByRevisionDescIdDesc(
                requireText(connectionId, "connectionId")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredLoginConnectionRevision> findByConnectionIdAndId(
            String connectionId,
            String revisionId
    ) {
        return delegate.findByConnectionIdAndId(
                requireText(connectionId, "connectionId"),
                requireText(revisionId, "revisionId")
        );
    }

    @Override
    @Transactional
    public StoredLoginConnectionRevision save(StoredLoginConnectionRevision revision) {
        return delegate.saveAndFlush(Objects.requireNonNull(revision, "revision"));
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
