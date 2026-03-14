package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.repository.ApiTokenRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @BeforeEach
    void setUp() {
        service = new ApiTokenService(tokenRepo);
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
}
