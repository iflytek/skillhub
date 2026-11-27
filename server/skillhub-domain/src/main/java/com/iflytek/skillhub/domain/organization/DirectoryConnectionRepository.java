package com.iflytek.skillhub.domain.organization;

import java.util.List;
import java.util.Optional;

/** Organization-scoped repository port for provisioning connections. */
public interface DirectoryConnectionRepository {

    Optional<DirectoryConnection> findById(String connectionId);

    Optional<DirectoryConnection> findByOrganizationIdAndId(String organizationId, String connectionId);

    Optional<DirectoryConnection> findByOrganizationIdAndIdForUpdate(String organizationId, String connectionId);

    List<DirectoryConnection> findByOrganizationId(String organizationId);

    DirectoryConnection save(DirectoryConnection connection);
}
