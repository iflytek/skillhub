package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.local.LocalCredentialRepository;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.settings.LocalAuthSettingsService;
import com.iflytek.skillhub.dto.AuthMeResponse;
import org.springframework.stereotype.Service;

/**
 * Builds the current-user API response with account capabilities derived from
 * authoritative backend state.
 */
@Service
public class AuthMeResponseAssembler {

    private final LocalCredentialRepository localCredentialRepository;
    private final LocalAuthSettingsService authSettings;

    public AuthMeResponseAssembler(LocalCredentialRepository localCredentialRepository,
                                   LocalAuthSettingsService authSettings) {
        this.localCredentialRepository = localCredentialRepository;
        this.authSettings = authSettings;
    }

    public AuthMeResponse from(PlatformPrincipal principal) {
        return AuthMeResponse.from(
                principal,
                authSettings.current().passwordLoginEnabled()
                        && localCredentialRepository.existsByUserId(principal.userId())
        );
    }
}
