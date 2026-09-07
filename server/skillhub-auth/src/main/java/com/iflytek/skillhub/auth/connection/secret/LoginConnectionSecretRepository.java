package com.iflytek.skillhub.auth.connection.secret;

import java.util.List;
import java.util.Optional;

/** Tenant-scoped Secret persistence port; raw envelope access stays inside the Secret package. */
public interface LoginConnectionSecretRepository {

    List<LoginConnectionSecretVersion> findAllForUpdate(
            String scopeKey,
            String connectionId
    );

    List<LoginConnectionSecretVersion> findAll(
            String scopeKey,
            String connectionId,
            SecretPurpose purpose
    );

    Optional<LoginConnectionSecretVersion> findByReference(SecretReference reference);

    LoginConnectionSecretVersion saveAndFlush(LoginConnectionSecretVersion secretVersion);
}
