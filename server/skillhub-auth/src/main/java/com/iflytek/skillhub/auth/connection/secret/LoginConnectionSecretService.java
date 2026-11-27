package com.iflytek.skillhub.auth.connection.secret;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Encrypts a new credential version in the caller's locked connection transaction. */
@Service
public class LoginConnectionSecretService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_BYTES = 12;
    private static final int AUTHENTICATION_TAG_BITS = 128;
    private static final int MAX_SECRET_BYTES = 16_384;

    private final LoginConnectionSecretRepository repository;
    private final EnterpriseSecretEnvelopeProperties properties;

    public LoginConnectionSecretService(LoginConnectionSecretRepository repository,
                                        EnterpriseSecretEnvelopeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * The caller holds the target organization connection row lock and checks the current draft.
     * The caller owns and clears the input bytes. Old credential versions remain available to
     * any active revision until a separately tested replacement revision is activated.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long write(String organizationId, String connectionId, String actorId, byte[] secret, Instant now) {
        if (secret == null || secret.length == 0 || secret.length > MAX_SECRET_BYTES) {
            throw new DomainBadRequestException("error.loginConnection.secret.invalid");
        }

        long version = Math.incrementExact(repository.latestVersion(organizationId, connectionId));
        String keyId = properties.getActiveKeyId();
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] activeKey = null;
        try {
            activeKey = properties.activeKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(activeKey, "AES"),
                    new GCMParameterSpec(AUTHENTICATION_TAG_BITS, nonce));
            cipher.updateAAD(bindingContext(organizationId, connectionId, version, keyId));
            byte[] ciphertext = cipher.doFinal(secret);
            repository.saveAndFlush(new LoginConnectionSecretVersion(connectionId, version,
                    organizationId, keyId, nonce, ciphertext, actorId, now));
            return version;
        } catch (GeneralSecurityException | IllegalStateException unavailable) {
            throw new DomainConflictException("error.loginConnection.secret.unavailable");
        } finally {
            if (activeKey != null) {
                Arrays.fill(activeKey, (byte) 0);
            }
        }
    }

    static byte[] bindingContext(String organizationId, String connectionId, long version, String keyId) {
        return String.join("\u0000", "login-connection-secret-v1", organizationId, connectionId,
                LoginConnectionSecretVersion.PURPOSE, Long.toString(version), keyId)
                .getBytes(StandardCharsets.UTF_8);
    }
}
