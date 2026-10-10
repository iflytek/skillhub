package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.ProfileReviewService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.repository.JpaProfileReviewQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminProfileReviewAppServiceTest {

    @Mock
    private ProfileReviewService profileReviewService;

    @Mock
    private UserAccountRepository userAccountRepository;

    private AdminProfileReviewAppService service;
    private JpaProfileReviewQueryRepository profileReviewQueryRepository;

    @BeforeEach
    void setUp() {
        profileReviewQueryRepository = new JpaProfileReviewQueryRepository(userAccountRepository);
        service = new AdminProfileReviewAppService(profileReviewService, profileReviewQueryRepository);
    }

    @Test
    void list_usesOldDisplayNameSnapshotForApprovedRequests() {
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-1",
                "{\"displayName\":\"NewName\"}",
                "{\"displayName\":\"OldName\"}",
                ProfileChangeStatus.APPROVED,
                "PASS",
                null
        );
        ReflectionTestUtils.setField(request, "id", 1L);
        ReflectionTestUtils.setField(request, "createdAt", Instant.parse("2026-03-19T07:00:00Z"));
        ReflectionTestUtils.setField(request, "reviewerId", "admin-1");
        ReflectionTestUtils.setField(request, "reviewedAt", Instant.parse("2026-03-19T07:05:00Z"));

        UserAccount submitter = new UserAccount("user-1", "NewestName", "user@example.com", null);
        UserAccount reviewer = new UserAccount("admin-1", "Admin Reviewer", "admin@example.com", null);

        given(profileReviewService.listByStatus(ProfileChangeStatus.APPROVED, PageRequest.of(0, 20), "DESC"))
                .willReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        given(userAccountRepository.findByIdIn(List.of("user-1", "admin-1")))
                .willReturn(List.of(submitter, reviewer));

        var response = service.list("APPROVED", 0, 20, "DESC");

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).currentDisplayName()).isEqualTo("OldName");
        assertThat(response.items().get(0).requestedDisplayName()).isEqualTo("NewName");
    }
}
