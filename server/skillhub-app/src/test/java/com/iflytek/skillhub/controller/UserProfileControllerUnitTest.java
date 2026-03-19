package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.session.PlatformSessionService;
import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeRequestRepository;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserProfileService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
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
                platformSessionService
        );
        setLocale(Locale.getDefault());
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
        assertThat(response.data().pendingChanges()).isNotNull();
        assertThat(response.data().pendingChanges().status()).isEqualTo("REJECTED");
    }
}
