package com.iflytek.skillhub.auth.connection.control;

import java.util.Optional;

/** Connection-scoped persistence port for immutable Login Connection revisions. */
public interface LoginConnectionRevisionRepository {

    Optional<StoredLoginConnectionRevision> findLatestByConnectionId(String connectionId);

    Optional<StoredLoginConnectionRevision> findByConnectionIdAndId(
            String connectionId,
            String revisionId
    );

    StoredLoginConnectionRevision save(StoredLoginConnectionRevision revision);
}
