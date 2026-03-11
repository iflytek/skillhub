package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiTokenService {

    private static final String TOKEN_PREFIX = "sk_";
    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ApiTokenRepository tokenRepo;

    public ApiTokenService(ApiTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    public record TokenCreateResult(String rawToken, ApiToken entity) {}

    @Transactional
    public TokenCreateResult createToken(Long userId, String name, String scopeJson) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = sha256(rawToken);
        String prefix = rawToken.substring(0, Math.min(rawToken.length(), 8));

        ApiToken token = new ApiToken(userId, name, prefix, tokenHash, scopeJson);
        token = tokenRepo.save(token);
        return new TokenCreateResult(rawToken, token);
    }

    public Optional<ApiToken> validateToken(String rawToken) {
        String hash = sha256(rawToken);
        return tokenRepo.findByTokenHash(hash).filter(ApiToken::isValid);
    }

    @Transactional
    public void revokeToken(Long tokenId, Long userId) {
        tokenRepo.findById(tokenId)
            .filter(t -> t.getUserId().equals(userId))
            .ifPresent(t -> {
                t.setRevokedAt(LocalDateTime.now());
                tokenRepo.save(t);
            });
    }

    public List<ApiToken> listActiveTokens(Long userId) {
        return tokenRepo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void touchLastUsed(ApiToken token) {
        token.setLastUsedAt(LocalDateTime.now());
        tokenRepo.save(token);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
