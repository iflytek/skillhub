package com.iflytek.skillhub.auth.uass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UassSessionFlowServiceTest {

    private final UassClientFacade uassClientFacade = mock(UassClientFacade.class);
    private final UassLoginStateService uassLoginStateService = mock(UassLoginStateService.class);
    private final UassSessionContextService uassSessionContextService = new UassSessionContextService();

    private UassSessionFlowService service;

    @BeforeEach
    void setUp() {
        service = new UassSessionFlowService(uassClientFacade, uassLoginStateService, uassSessionContextService);
    }

    @Test
    void status_prefersLocalSessionAndExposesOptionalRemoteResultForUassSessions() {
        MockHttpServletRequest request = requestWithBoundSession();
        when(uassClientFacade.checkLoginStatus(loginContext())).thenReturn(true);

        UassSessionFlowService.UassSessionStatus status = service.status(authentication("uass"), request);

        assertThat(status.authenticated()).isTrue();
        assertThat(status.provider()).isEqualTo("uass");
        assertThat(status.remoteAuthenticated()).isTrue();
        verify(uassClientFacade).checkLoginStatus(loginContext());
    }

    @Test
    void status_keepsLocalSessionAsSourceOfTruthWhenRemoteCheckFails() {
        MockHttpServletRequest request = requestWithBoundSession();
        doThrow(new UassClientException("checkLoginStatus", "remote down"))
                .when(uassClientFacade).checkLoginStatus(loginContext());

        UassSessionFlowService.UassSessionStatus status = service.status(authentication("uass"), request);

        assertThat(status.authenticated()).isTrue();
        assertThat(status.provider()).isEqualTo("uass");
        assertThat(status.remoteAuthenticated()).isNull();
    }

    @Test
    void status_doesNotCallRemoteChecksForNonUassProviders() {
        MockHttpServletRequest request = requestWithBoundSession();

        UassSessionFlowService.UassSessionStatus status = service.status(authentication("github"), request);

        assertThat(status.authenticated()).isTrue();
        assertThat(status.provider()).isEqualTo("github");
        assertThat(status.remoteAuthenticated()).isNull();
        verify(uassClientFacade, never()).checkLoginStatus(loginContext());
    }

    @Test
    void status_returnsLoggedOutWhenAuthenticationMissing() {
        UassSessionFlowService.UassSessionStatus status = service.status(null, new MockHttpServletRequest());

        assertThat(status.authenticated()).isFalse();
        assertThat(status.provider()).isNull();
        assertThat(status.remoteAuthenticated()).isNull();
    }

    @Test
    void status_ignoresAuthenticatedPrincipalsOutsidePlatformPrincipal() {
        MockHttpServletRequest request = requestWithBoundSession();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("plain-user", null, java.util.List.of());

        UassSessionFlowService.UassSessionStatus status = service.status(authentication, request);

        assertThat(status.authenticated()).isFalse();
        assertThat(status.provider()).isNull();
        assertThat(status.remoteAuthenticated()).isNull();
        verify(uassClientFacade, never()).checkLoginStatus(loginContext());
    }

    @Test
    void logout_attemptsRemoteLogoutAndAlwaysClearsLocalState() {
        MockHttpServletRequest request = requestWithBoundSession();
        MockHttpSession session = (MockHttpSession) request.getSession(false);

        service.logout(request);

        verify(uassClientFacade).logout(loginContext());
        verify(uassLoginStateService).clearFailedCallback("state-1");
        assertThat(session.isInvalid()).isTrue();
        assertThat(uassSessionContextService.load(request)).isEmpty();
    }

    @Test
    void logout_withoutBoundContextStillClearsSecurityState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SecurityContextHolder.getContext().setAuthentication(authentication("uass"));

        service.logout(request);

        verify(uassClientFacade, never()).logout(loginContext());
        verify(uassLoginStateService, never()).clearFailedCallback("state-1");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logout_clearsSessionEvenWhenRemoteLogoutFails() {
        MockHttpServletRequest request = requestWithBoundSession();
        MockHttpSession session = (MockHttpSession) request.getSession(false);
        doThrow(new UassClientException("logout", "remote down")).when(uassClientFacade).logout(loginContext());

        service.logout(request);

        verify(uassClientFacade).logout(loginContext());
        verify(uassLoginStateService).clearFailedCallback("state-1");
        assertThat(session.isInvalid()).isTrue();
        assertThat(uassSessionContextService.load(request)).isEmpty();
    }

    private MockHttpServletRequest requestWithBoundSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        uassSessionContextService.bind(loginContext(), request);
        return request;
    }

    private static UsernamePasswordAuthenticationToken authentication(String provider) {
        PlatformPrincipal principal = new PlatformPrincipal(
                "usr_1",
                "UASS User",
                "user@example.com",
                null,
                provider,
                Set.of("USER")
        );
        return new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
    }

    private static UassLoginContext loginContext() {
        return new UassLoginContext(
                "state-1",
                URI.create("https://skillhub.example.com/api/v1/auth/uass/callback"),
                "U1001",
                "access-token",
                "refresh-token",
                Instant.parse("2026-04-30T08:00:00Z"),
                Map.of("tenant", "acme")
        );
    }
}
