package com.iflytek.skillhub.auth.connection.secret;

import java.util.Optional;

/** Key-provider seam for local encrypted storage or an externally managed KMS/Vault. */
public interface SecretEnvelopeKeyring {

    SecretEnvelopeKey activeKey();

    Optional<SecretEnvelopeKey> findById(String keyId);
}
