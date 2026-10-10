package com.iflytek.skillhub.infra.identity;

import com.iflytek.skillhub.domain.organization.OrganizationDomainChallengeToken;
import com.iflytek.skillhub.domain.organization.OrganizationDomainChallengeTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** CSPRNG-backed DNS proof generator that persists only SHA-256 digests. */
@Component
public class SecureOrganizationDomainChallengeTokenService
        implements OrganizationDomainChallengeTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_CANDIDATE_LENGTH = 1024;

    private final SecureRandom secureRandom;

    public SecureOrganizationDomainChallengeTokenService() {
        this(new SecureRandom());
    }

    SecureOrganizationDomainChallengeTokenService(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public OrganizationDomainChallengeToken issue() {
        byte[] entropy = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(entropy);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        return OrganizationDomainChallengeToken.issued(plaintext, sha256(plaintext));
    }

    @Override
    public boolean matches(String candidate, String storedDigest) {
        if (candidate == null
                || candidate.isBlank()
                || candidate.length() > MAX_CANDIDATE_LENGTH
                || storedDigest == null
                || storedDigest.isBlank()) {
            return false;
        }
        byte[] actual = sha256(candidate).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = storedDigest.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
