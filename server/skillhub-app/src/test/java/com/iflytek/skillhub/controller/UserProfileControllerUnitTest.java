package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.ProfileFieldPolicyConfig;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UpdateProfileResult;
import com.iflytek.skillhub.domain.user.UserProfileService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.ProfileUpdateStatus;
import com.iflytek.skillhub.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.context.i18n.LocaleContextHolder.setLocale;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerUnitTest {

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private ProfileChangeRequestRepository changeRequestRepository;

    @Mock
    private PlatformSessionService platformSessionService;

    @Mock
    private ProfileFieldPolicyConfig fieldPolicyConfig;

    private UserProfileController controller;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("response.success.read", Locale.getDefault(), "response.success.read");
        ApiResponseFactory responseFactory = new ApiResponseFactory(
                messageSource,
                Clock.fixed(Instant.parse("2026-03-19T08:00:00Z"), ZoneOffset.UTC)
        );
        controller = new UserProfileController(
                responseFactory,
                userProfileService,
                userAccountRepository,
                changeRequestRepository,
                platformSessionService,
                fieldPolicyConfig
        );
        org.mockito.Mockito.lenient().when(fieldPolicyConfig.fieldPolicies()).thenReturn(Map.of(
                "displayName", new ProfileFieldPolicyConfig.FieldPolicy(true, false),
                "email", new ProfileFieldPolicyConfig.FieldPolicy(false, false)));
        setLocale(Locale.getDefault());
    }

    @Test
    void getProfile_userNotFound_throwsUnauthorizedException() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "ApprovedName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );

        given(userAccountRepository.findById("user-1")).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.getProfile(principal))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class)
                .hasMessage("error.auth.required");
    }

    @Test
    void getProfile_noPendingChanges_returnsNullPendingChanges() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "ApprovedName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        UserAccount user = new UserAccount("user-1", "ApprovedName", "user@example.com", "https://example.com/avatar.png");

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(changeRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                "user-1",
                List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED)))
                .willReturn(Optional.empty());

        var response = controller.getProfile(principal);

        assertThat(response.data().pendingChanges()).isNull();
        assertThat(response.data().displayName()).isEqualTo("ApprovedName");
    }

    @Test
    void getProfile_returnsLatestPrivateValuesWhenPendingReviewExists() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "ApprovedName",
                "user@example.com",
                "https://example.com/avatar.png",
                "github",
                Set.of("USER")
        );
        UserAccount user = new UserAccount("user-1", "ApprovedName", "user@example.com", "https://example.com/avatar.png");
        user.setUssId("uass-001");
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-1",
                "{\"displayName\":\"LatestPendingName\",\"avatarUrl\":\"https://example.com/new-avatar.png\"}",
                "{\"displayName\":\"ApprovedName\",\"avatarUrl\":\"https://example.com/avatar.png\"}",
                ProfileChangeStatus.PENDING,
                "PASS",
                null
        );

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(changeRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                "user-1",
                List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED)))
                .willReturn(Optional.of(request));

        var response = controller.getProfile(principal);

        assertThat(response.data().displayName()).isEqualTo("LatestPendingName");
        assertThat(response.data().avatarUrl()).isEqualTo("https://example.com/new-avatar.png");
        assertThat(response.data().ussId()).isEqualTo("uass-001");
        assertThat(response.data().pendingChanges()).isNotNull();
        assertThat(response.data().pendingChanges().status()).isEqualTo("PENDING");
        assertThat(response.data().pendingChanges().changes()).containsEntry("displayName", "LatestPendingName");
    }

    @Test
    void getProfile_keepsApprovedValuesWhenLatestRequestWasRejected() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1",
                "ApprovedName",
                "user@example.com",
                "https://example.com/avatar.png",
                "github",
                Set.of("USER")
        );
        UserAccount user = new UserAccount("user-1", "ApprovedName", "user@example.com", "https://example.com/avatar.png");
        user.setUssId("uass-001");
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-1",
                "{\"displayName\":\"RejectedName\"}",
                "{\"displayName\":\"ApprovedName\"}",
                ProfileChangeStatus.REJECTED,
                "PASS",
                null
        );
        request.setReviewComment("not allowed");

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(changeRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                "user-1",
                List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED)))
                .willReturn(Optional.of(request));

        var response = controller.getProfile(principal);

        assertThat(response.data().displayName()).isEqualTo("ApprovedName");
        assertThat(response.data().avatarUrl()).isEqualTo("https://example.com/avatar.png");
        assertThat(response.data().ussId()).isEqualTo("uass-001");
        assertThat(response.data().pendingChanges()).isNotNull();
        assertThat(response.data().pendingChanges().status()).isEqualTo("REJECTED");
    }

    @Test
    void getProfile_malformedPendingChangesJson_returnsNullPendingChanges() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "ApprovedName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        UserAccount user = new UserAccount("user-1", "ApprovedName", "user@example.com", "https://example.com/avatar.png");
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-1",
                "not-valid-json",
                "{}",
                ProfileChangeStatus.PENDING,
                "PASS",
                null
        );

        given(userAccountRepository.findById("user-1")).willReturn(Optional.of(user));
        given(changeRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                "user-1",
                List.of(ProfileChangeStatus.PENDING, ProfileChangeStatus.REJECTED)))
                .willReturn(Optional.of(request));

        var response = controller.getProfile(principal);

        assertThat(response.data().pendingChanges()).isNull();
    }

    @Test
    void updateProfile_pendingReview_returnsPendingReviewStatus() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "OldName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        var request = new UpdateProfileRequest("NewName");
        var mockHttpRequest = new MockHttpServletRequest();
        mockHttpRequest.addHeader("X-Request-Id", "req-1");

        given(userProfileService.updateProfile(
                "user-1",
                Map.of("displayName", "NewName"),
                "req-1",
                "127.0.0.1",
                null))
                .willReturn(com.iflytek.skillhub.domain.user.UpdateProfileResult.pendingReview());

        var response = controller.updateProfile(
                principal,
                null,
                request,
                mockHttpRequest);

        assertThat(response.data().status()).isEqualTo(ProfileUpdateStatus.PENDING_REVIEW);
    }

    @Test
    void updateProfile_mixedResult_returnsPartiallyAppliedStatus() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "OldName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        var request = new UpdateProfileRequest("NewName");
        var mockHttpRequest = new MockHttpServletRequest();
        mockHttpRequest.addHeader("X-Request-Id", "req-1");

        given(userProfileService.updateProfile(
                "user-1",
                Map.of("displayName", "NewName"),
                "req-1",
                "127.0.0.1",
                null))
                .willReturn(com.iflytek.skillhub.domain.user.UpdateProfileResult.mixed(
                        Map.of("displayName", "NewName"),
                        Map.of("avatarUrl", "pending-avatar")));

        var response = controller.updateProfile(
                principal,
                null,
                request,
                mockHttpRequest);

        assertThat(response.data().status()).isEqualTo(ProfileUpdateStatus.PARTIALLY_APPLIED);
        assertThat(response.data().appliedFields()).containsEntry("displayName", "NewName");
        assertThat(response.data().pendingFields()).containsEntry("avatarUrl", "pending-avatar");
    }

    @Test
    void updateProfile_nullDisplayName_throwsNoChanges() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "OldName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        var request = new UpdateProfileRequest((String) null);
        var mockHttpRequest = new MockHttpServletRequest();

        assertThatThrownBy(() -> controller.updateProfile(principal, null, request, mockHttpRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("error.profile.noChanges");
    }

    @Test
    void resolveClientIp_xRealIP() throws Exception {
        java.lang.reflect.Method method = UserProfileController.class.getDeclaredMethod("resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "192.168.1.1");

        String result = (String) method.invoke(controller, request);
        assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    void resolveClientIp_remoteAddr() throws Exception {
        java.lang.reflect.Method method = UserProfileController.class.getDeclaredMethod("resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        String result = (String) method.invoke(controller, request);
        assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    void resolveClientIp_commaSeparatedXForwardedFor() throws Exception {
        java.lang.reflect.Method method = UserProfileController.class.getDeclaredMethod("resolveClientIp", jakarta.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.2, 10.0.0.3");

        String result = (String) method.invoke(controller, request);
        assertThat(result).isEqualTo("10.0.0.2");
    }

    @Test
    void getProfile_nullPrincipal_throwsUnauthorizedException() {
        assertThatThrownBy(() -> controller.getProfile(null))
                .isInstanceOf(com.iflytek.skillhub.exception.UnauthorizedException.class)
                .hasMessage("error.auth.required");
    }

    @Test
    void updateProfile_applied_returnsAppliedStatus() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "OldName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        var request = new UpdateProfileRequest("NewName");
        var mockHttpRequest = new MockHttpServletRequest();
        mockHttpRequest.addHeader("X-Request-Id", "req-1");

        given(userProfileService.updateProfile(
                "user-1",
                Map.of("displayName", "NewName"),
                "req-1",
                "127.0.0.1",
                null))
                .willReturn(com.iflytek.skillhub.domain.user.UpdateProfileResult.applied());

        var response = controller.updateProfile(principal, null, request, mockHttpRequest);

        assertThat(response.data().status()).isEqualTo(ProfileUpdateStatus.APPLIED);
    }

    @Test
    void updateProfile_unknownResultType_throwsIllegalStateException() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "OldName", "user@example.com",
                "https://example.com/avatar.png", "github", Set.of("USER")
        );
        var request = new UpdateProfileRequest("NewName");
        var mockHttpRequest = new MockHttpServletRequest();
        mockHttpRequest.addHeader("X-Request-Id", "req-1");

        given(userProfileService.updateProfile(
                "user-1",
                Map.of("displayName", "NewName"),
                "req-1",
                "127.0.0.1",
                null))
                .willReturn(null);

        assertThatThrownBy(() -> controller.updateProfile(principal, null, request, mockHttpRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown update profile result: null");
    }
}
