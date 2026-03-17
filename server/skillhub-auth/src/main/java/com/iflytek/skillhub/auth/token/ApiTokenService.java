package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiTokenService {

    private static final String TOKEN_PREFIX = "sk_";
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_NAME_LENGTH = 64;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ApiTokenRepository tokenRepo;

    public ApiTokenService(ApiTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    public record TokenCreateResult(String rawToken, ApiToken entity) {}

    @Transactional
    public TokenCreateResult createToken(String userId, String name, String scopeJson) {
        return createToken(userId, name, scopeJson, null);
    }

    @Transactional
    public TokenCreateResult createToken(String userId, String name, String scopeJson, String expiresAt) {
        String normalizedName = normalizeName(name);
        validateTokenName(userId, normalizedName);
        LocalDateTime parsedExpiresAt = parseExpiresAt(expiresAt);

        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = sha256(rawToken);
        String prefix = rawToken.substring(0, Math.min(rawToken.length(), 8));

        ApiToken token = new ApiToken(userId, normalizedName, prefix, tokenHash, scopeJson);
        token.setExpiresAt(parsedExpiresAt);
        try {
            token = tokenRepo.save(token);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainBadRequestException("error.token.name.duplicate");
        }
        return new TokenCreateResult(rawToken, token);
    }

    /**
     * Revoke existing token with the same name (if any) and create a new one.
     * Used by device auth flow to avoid duplicate-name errors on repeated logins.
     */
    @Transactional
    public TokenCreateResult rotateToken(String userId, String name, String scopeJson) {
        return rotateToken(userId, name, scopeJson, null);
    }

    @Transactional
    public TokenCreateResult rotateToken(String userId, String name, String scopeJson, String expiresAt) {
        String normalizedName = normalizeName(name);
        tokenRepo.findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull(userId, normalizedName)
                .ifPresent(existing -> {
                    existing.setRevokedAt(LocalDateTime.now());
                    tokenRepo.save(existing);
                });
        return createToken(userId, name, scopeJson, expiresAt);
    }

    public Optional<ApiToken> validateToken(String rawToken) {
        String hash = sha256(rawToken);
        return tokenRepo.findByTokenHash(hash).filter(ApiToken::isValid);
    }

    @Transactional
    public void revokeToken(Long tokenId, String userId) {
        tokenRepo.findById(tokenId)
            .filter(t -> t.getUserId().equals(userId))
            .ifPresent(t -> {
                t.setRevokedAt(LocalDateTime.now());
                tokenRepo.save(t);
            });
    }

    @Transactional
    public ApiToken updateExpiration(Long tokenId, String userId, String expiresAt) {
        ApiToken token = tokenRepo.findById(tokenId)
                .filter(existing -> existing.getUserId().equals(userId) && existing.getRevokedAt() == null)
                .orElseThrow(() -> new DomainNotFoundException("error.token.notFound", tokenId));
        token.setExpiresAt(parseExpiresAt(expiresAt));
        return tokenRepo.save(token);
    }

    public List<ApiToken> listActiveTokens(String userId) {
        return tokenRepo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId);
    }

    public Page<ApiToken> listActiveTokens(String userId, int page, int size) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(size, 1);
        return tokenRepo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(resolvedPage, resolvedSize));
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

    private String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.trim();
    }

    private void validateTokenName(String userId, String name) {
        if (name.isBlank()) {
            throw new DomainBadRequestException("validation.token.name.notBlank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new DomainBadRequestException("validation.token.name.size");
        }
        if (tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase(userId, name)) {
            throw new DomainBadRequestException("error.token.name.duplicate");
        }
    }

    private LocalDateTime parseExpiresAt(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return null;
        }

        try {
            LocalDateTime parsed = LocalDateTime.parse(expiresAt.trim());
            if (!parsed.isAfter(LocalDateTime.now())) {
                throw new DomainBadRequestException("validation.token.expiresAt.future");
            }
            return parsed;
        } catch (DateTimeParseException ex) {
            throw new DomainBadRequestException("validation.token.expiresAt.invalid");
        }
    }
}
