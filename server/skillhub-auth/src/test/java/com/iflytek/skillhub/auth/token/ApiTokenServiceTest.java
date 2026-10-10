package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceTest {

    @Mock
    private ApiTokenRepository tokenRepo;

    private ApiTokenService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-03-18T00:00:00Z"), ZoneOffset.UTC);
        service = new ApiTokenService(tokenRepo, clock);
    }

    @Test
    void createToken_rejectsNamesLongerThan64Characters() {
        String longName = "a".repeat(65);

        assertThatThrownBy(() -> service.createToken("user-1", longName, "[]"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("validation.token.name.size");

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void createToken_rejectsDuplicateActiveNamesIgnoringCase() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "My Token"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createToken("user-1", "  My Token  ", "[]"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.token.name.duplicate");

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void createToken_trimsNameBeforeCheckingDuplicates() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "My Token"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createToken("user-1", "  My Token  ", "[]"))
                .isInstanceOf(DomainBadRequestException.class);

        verify(tokenRepo).existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "My Token");
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void createToken_setsExpirationWhenProvided() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", "CLI", "[]", "2099-03-20T10:15:00");

        assertThat(result.entity().getExpiresAt()).isEqualTo(Instant.parse("2099-03-20T10:15:00Z"));
    }

    @Test
    void createToken_rejectsPastExpiration() {
        assertThatThrownBy(() -> service.createToken("user-1", "CLI", "[]", "2000-01-01T00:00:00"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("validation.token.expiresAt.future");

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void createToken_rejectsBlankNamesAfterTrimming() {
        assertThatThrownBy(() -> service.createToken("user-1", "   ", "[]"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("validation.token.name.notBlank");

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void createToken_allowsReusingNameWhenPreviousTokenIsRevoked() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", " CLI ", "[]");

        assertThat(result.entity().getName()).isEqualTo("CLI");
        verify(tokenRepo).existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI");
        verify(tokenRepo).save(any(ApiToken.class));
    }

    @Test
    void createToken_translatesDatabaseConstraintViolationToDuplicateError() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.createToken("user-1", "CLI", "[]"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("error.token.name.duplicate");
    }

    @Test
    void validateToken_returnsEmptyForUnknownToken() {
        when(tokenRepo.findByTokenHash(sha256("missing-token"))).thenReturn(Optional.empty());

        assertThat(service.validateToken("missing-token")).isEmpty();
    }

    @Test
    void validateToken_returnsEmptyForExpiredToken() {
        ApiToken token = new ApiToken("user-1", "CLI", "sk_test", sha256("expired-token"), "[]");
        token.setExpiresAt(Instant.parse("2026-03-17T23:59:59Z"));
        when(tokenRepo.findByTokenHash(sha256("expired-token"))).thenReturn(Optional.of(token));

        assertThat(service.validateToken("expired-token")).isEmpty();
    }

    @Test
    void validateToken_returnsEmptyForRevokedToken() {
        ApiToken token = new ApiToken("user-1", "CLI", "sk_test", sha256("revoked-token"), "[]");
        token.setRevokedAt(Instant.parse("2026-03-17T23:59:59Z"));
        when(tokenRepo.findByTokenHash(sha256("revoked-token"))).thenReturn(Optional.of(token));

        assertThat(service.validateToken("revoked-token")).isEmpty();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
