package com.iflytek.skillhub.auth.connection.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.shared.exception.DomainConflictException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LoginConnectionSecretServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

    @Test
    void encryptsANewVersionAndBindsItToItsTenantAndConnection() throws Exception {
        byte[] testKey = new byte[32];
        EnterpriseSecretEnvelopeProperties properties = properties(testKey);
        var repository = mock(LoginConnectionSecretRepository.class);
        var service = new LoginConnectionSecretService(repository, properties);
        when(repository.latestVersion("org-a", "connection-a")).thenReturn(2L);
        byte[] secret = "fixture-secret".getBytes(StandardCharsets.UTF_8);

        assertThat(service.write("org-a", "connection-a", "actor-a", secret, NOW)).isEqualTo(3);

        var saved = ArgumentCaptor.forClass(LoginConnectionSecretVersion.class);
        verify(repository).saveAndFlush(saved.capture());
        var version = saved.getValue();
        var nonce = (byte[]) ReflectionTestUtils.getField(version, "nonce");
        var ciphertext = (byte[]) ReflectionTestUtils.getField(version, "ciphertext");
        assertThat(ReflectionTestUtils.getField(version, "keyId")).isEqualTo("test-key");
        assertThat(nonce).hasSize(12);
        assertThat(ciphertext).hasSize(secret.length + 16).isNotEqualTo(secret);
        assertThat(decrypt(testKey, nonce, ciphertext, "org-a")).isEqualTo(secret);
        assertThatThrownBy(() -> decrypt(testKey, nonce, ciphertext, "org-b"))
                .isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void failsClosedWhenActiveKeyIsUnavailable() {
        var repository = mock(LoginConnectionSecretRepository.class);
        var properties = new EnterpriseSecretEnvelopeProperties();
        properties.setActiveKeyId("missing");
        properties.setKeys(List.of("test-key:malformed"));
        var service = new LoginConnectionSecretService(repository, properties);

        assertThatThrownBy(() -> service.write("org-a", "connection-a", "actor-a", new byte[] {1}, NOW))
                .isInstanceOf(DomainConflictException.class)
                .hasMessage("error.loginConnection.secret.unavailable");
        verify(repository, never()).saveAndFlush(any());
    }

    private static EnterpriseSecretEnvelopeProperties properties(byte[] key) {
        var properties = new EnterpriseSecretEnvelopeProperties();
        properties.setActiveKeyId("test-key");
        properties.setKeys(List.of("test-key:" + Base64.getEncoder().encodeToString(key)));
        return properties;
    }

    private static byte[] decrypt(byte[] key, byte[] nonce, byte[] ciphertext, String organizationId)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(LoginConnectionSecretService.bindingContext(organizationId, "connection-a", 3, "test-key"));
        return cipher.doFinal(ciphertext);
    }
}
