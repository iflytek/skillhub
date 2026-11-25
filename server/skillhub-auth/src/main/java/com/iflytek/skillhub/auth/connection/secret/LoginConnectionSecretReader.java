package com.iflytek.skillhub.auth.connection.secret;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** Reads one active connection secret only for the runtime protocol exchange. */
@Service
public class LoginConnectionSecretReader {
    private static final int AUTHENTICATION_TAG_BITS = 128;

    private final LoginConnectionSecretRepository repository;
    private final EnterpriseSecretEnvelopeProperties properties;

    public LoginConnectionSecretReader(LoginConnectionSecretRepository repository,
                                       EnterpriseSecretEnvelopeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public Optional<String> read(String organizationId, String connectionId, long version) {
        return repository.findById(new LoginConnectionSecretVersion.Id(connectionId, version))
                .filter(secret -> organizationId.equals(secret.getOrganizationId()))
                .filter(secret -> LoginConnectionSecretVersion.PURPOSE.equals(secret.getPurpose()))
                .filter(secret -> "AVAILABLE".equals(secret.getStatus()))
                .map(this::decrypt);
    }

    private String decrypt(LoginConnectionSecretVersion secret) {
        byte[] key = null;
        try {
            key = properties.key(secret.getKeyId());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(AUTHENTICATION_TAG_BITS, secret.getNonce()));
            cipher.updateAAD(LoginConnectionSecretService.bindingContext(
                    secret.getOrganizationId(), secret.getId().getConnectionId(),
                    secret.getId().getBindingVersion(), secret.getKeyId()));
            return new String(cipher.doFinal(secret.getCiphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalStateException invalid) {
            throw new IllegalStateException("OIDC secret unavailable");
        } finally {
            if (key != null) {
                Arrays.fill(key, (byte) 0);
            }
        }
    }
}
