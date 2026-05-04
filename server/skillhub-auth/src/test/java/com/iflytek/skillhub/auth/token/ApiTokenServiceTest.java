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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;

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
    void createToken_threeArgDelegatesToFourArgWithNullExpiresAt() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", "CLI", "[]");

        assertThat(result.entity().getExpiresAt()).isNull();
        assertThat(result.rawToken()).startsWith("sk_");
    }

    @Test
    void createToken_rejectsInvalidExpiresAtFormat() {
        assertThatThrownBy(() -> service.createToken("user-1", "CLI", "[]", "not-a-date"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("validation.token.expiresAt.invalid");
    }

    @Test
    void createToken_acceptsOffsetDateTimeFormat() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", "CLI", "[]", "2099-03-20T10:15:00+08:00");

        assertThat(result.entity().getExpiresAt()).isEqualTo(Instant.parse("2099-03-20T02:15:00Z"));
    }

    @Test
    void createToken_acceptsLocalDateTimeFormatAsUtc() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", "CLI", "[]", "2099-03-20T10:15:00");

        assertThat(result.entity().getExpiresAt()).isEqualTo(Instant.parse("2099-03-20T10:15:00Z"));
    }

    @Test
    void createToken_nullNameNormalizesToEmpty() {
        assertThatThrownBy(() -> service.createToken("user-1", null, "[]"))
                .isInstanceOf(DomainBadRequestException.class)
                .hasMessageContaining("validation.token.name.notBlank");
    }

    @Test
    void rotateToken_threeArgDelegatesToFourArgWithNullExpiresAt() {
        ApiToken existing = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull("user-1", "CLI"))
                .thenReturn(Optional.of(existing));
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rotateToken("user-1", "CLI", "[]");

        assertThat(result.entity().getExpiresAt()).isNull();
        assertThat(existing.getRevokedAt()).isNotNull();
    }

    @Test
    void rotateToken_fourArgRevokesExistingAndCreatesNew() {
        ApiToken existing = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull("user-1", "CLI"))
                .thenReturn(Optional.of(existing));
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rotateToken("user-1", "CLI", "[]", "2099-03-20T10:15:00");

        assertThat(existing.getRevokedAt()).isNotNull();
        assertThat(result.entity().getExpiresAt()).isEqualTo(Instant.parse("2099-03-20T10:15:00Z"));
    }

    @Test
    void rotateToken_noExistingTokenStillCreatesNew() {
        when(tokenRepo.findByUserIdAndNameIgnoreCaseAndRevokedAtIsNull("user-1", "CLI"))
                .thenReturn(Optional.empty());
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rotateToken("user-1", "CLI", "[]");

        assertThat(result.entity()).isNotNull();
    }

    @Test
    void validateToken_returnsTokenWhenValid() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));

        Optional<ApiToken> result = service.validateToken("sk_somerawtoken");

        assertThat(result).isPresent().hasValue(token);
    }

    @Test
    void validateToken_returnsEmptyWhenNotFound() {
        when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.empty());

        Optional<ApiToken> result = service.validateToken("sk_somerawtoken");

        assertThat(result).isEmpty();
    }

    @Test
    void validateToken_returnsEmptyWhenTokenExpired() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        token.setExpiresAt(Instant.parse("2000-01-01T00:00:00Z"));
        when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));

        Optional<ApiToken> result = service.validateToken("sk_somerawtoken");

        assertThat(result).isEmpty();
    }

    @Test
    void revokeToken_revokesOwnedToken() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findById(1L)).thenReturn(Optional.of(token));
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.revokeToken(1L, "user-1");

        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeToken_ignoresForeignToken() {
        ApiToken token = new ApiToken("user-2", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findById(1L)).thenReturn(Optional.of(token));

        service.revokeToken(1L, "user-1");

        assertThat(token.getRevokedAt()).isNull();
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void revokeToken_ignoresMissingToken() {
        when(tokenRepo.findById(1L)).thenReturn(Optional.empty());

        service.revokeToken(1L, "user-1");

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void updateExpiration_updatesActiveToken() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findById(1L)).thenReturn(Optional.of(token));
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiToken updated = service.updateExpiration(1L, "user-1", "2099-03-20T10:15:00");

        assertThat(updated.getExpiresAt()).isEqualTo(Instant.parse("2099-03-20T10:15:00Z"));
    }

    @Test
    void updateExpiration_throwsWhenTokenNotFound() {
        when(tokenRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateExpiration(1L, "user-1", "2099-03-20T10:15:00"))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("error.token.notFound");
    }

    @Test
    void updateExpiration_throwsWhenTokenRevoked() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        token.setRevokedAt(Instant.now(clock));
        when(tokenRepo.findById(1L)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.updateExpiration(1L, "user-1", "2099-03-20T10:15:00"))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("error.token.notFound");
    }

    @Test
    void updateExpiration_throwsWhenTokenOwnedByAnotherUser() {
        ApiToken token = new ApiToken("user-2", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findById(1L)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.updateExpiration(1L, "user-1", "2099-03-20T10:15:00"))
                .isInstanceOf(DomainNotFoundException.class)
                .hasMessageContaining("error.token.notFound");
    }

    @Test
    void listActiveTokens_returnsList() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc("user-1"))
                .thenReturn(List.of(token));

        List<ApiToken> result = service.listActiveTokens("user-1");

        assertThat(result).hasSize(1).containsExactly(token);
    }

    @Test
    void listActiveTokens_page_returnsPage() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        Page<ApiToken> page = new PageImpl<>(List.of(token));
        when(tokenRepo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(eq("user-1"), any(PageRequest.class)))
                .thenReturn(page);

        Page<ApiToken> result = service.listActiveTokens("user-1", 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listActiveTokens_page_clampsNegativeValues() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        Page<ApiToken> page = new PageImpl<>(List.of(token));
        when(tokenRepo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(eq("user-1"), any(PageRequest.class)))
                .thenReturn(page);

        Page<ApiToken> result = service.listActiveTokens("user-1", -1, -5);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void touchLastUsed_updatesTimestamp() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.touchLastUsed(token);

        assertThat(token.getLastUsedAt()).isEqualTo(Instant.now(clock));
    }
}
