package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDirectoryConnectionRepositoryAdapter implements DirectoryConnectionRepository {

    private final DirectoryConnectionSpringDataRepository delegate;

    public JpaDirectoryConnectionRepositoryAdapter(DirectoryConnectionSpringDataRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<DirectoryConnection> findById(String connectionId) {
        return delegate.findById(connectionId);
    }

    @Override
    public Optional<DirectoryConnection> findByOrganizationIdAndId(
            String organizationId, String connectionId) {
        return delegate.findByOrganizationIdAndId(organizationId, connectionId);
    }

    @Override
    public Optional<DirectoryConnection> findByOrganizationIdAndIdForUpdate(
            String organizationId, String connectionId) {
        return delegate.findByOrganizationIdAndIdForUpdate(organizationId, connectionId);
    }

    @Override
    public List<DirectoryConnection> findByOrganizationId(String organizationId) {
        return delegate.findByOrganizationIdOrderByCreatedAtAscIdAsc(organizationId);
    }

    @Override
    public DirectoryConnection save(DirectoryConnection connection) {
        return delegate.saveAndFlush(connection);
    }
}
