package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.entity.ApiToken;
import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiTokenServiceCoverageTest {

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
    void sha256_handlesNoSuchAlgorithmException() throws Exception {
        try (MockedStatic<MessageDigest> mocked = mockStatic(MessageDigest.class)) {
            mocked.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException());

            Method method = ApiTokenService.class.getDeclaredMethod("sha256", String.class);
            method.setAccessible(true);

            Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> method.invoke(service, "test"));
            assertThat(thrown).isInstanceOf(java.lang.reflect.InvocationTargetException.class);
            assertThat(thrown.getCause()).isInstanceOf(RuntimeException.class)
                    .hasMessage("SHA-256 not available");
        }
    }

    @Test
    void parseInstant_offsetDateTimeWithoutSeconds() throws Exception {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", "CLI", "[]", "2099-03-20T10:15+08:00");

        assertThat(result.entity().getExpiresAt()).isEqualTo(Instant.parse("2099-03-20T02:15:00Z"));
    }

    @Test
    void createToken_blankExpiresAtTreatedAsNull() {
        when(tokenRepo.existsByUserIdAndRevokedAtIsNullAndNameIgnoreCase("user-1", "CLI"))
                .thenReturn(false);
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createToken("user-1", "CLI", "[]", "   ");

        assertThat(result.entity().getExpiresAt()).isNull();
    }

    @Test
    void validateToken_returnsEmptyWhenTokenRevoked() {
        ApiToken token = new ApiToken("user-1", "CLI", "pref", "hash", "[]");
        token.setRevokedAt(Instant.now(clock));
        when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.of(token));

        Optional<ApiToken> result = service.validateToken("sk_somerawtoken");

        assertThat(result).isEmpty();
    }
}
