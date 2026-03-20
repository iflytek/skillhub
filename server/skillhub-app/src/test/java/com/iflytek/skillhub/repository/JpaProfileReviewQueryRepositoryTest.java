package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.iflytek.skillhub.domain.user.ProfileChangeRequest;
import com.iflytek.skillhub.domain.user.ProfileChangeStatus;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JpaProfileReviewQueryRepositoryTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    private JpaProfileReviewQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaProfileReviewQueryRepository(userAccountRepository);
    }

    @Test
    void getProfileReviewSummaries_prefersOldDisplayNameSnapshot() {
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

        given(userAccountRepository.findByIdIn(List.of("user-1", "admin-1")))
                .willReturn(List.of(submitter, reviewer));

        var response = repository.getProfileReviewSummaries(List.of(request));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).username()).isEqualTo("NewestName");
        assertThat(response.get(0).currentDisplayName()).isEqualTo("OldName");
        assertThat(response.get(0).requestedDisplayName()).isEqualTo("NewName");
        assertThat(response.get(0).reviewerName()).isEqualTo("Admin Reviewer");
    }

    @Test
    void getProfileReviewSummaries_fallsBackToUserIdWhenSubmitterMissing() {
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-9",
                "{\"displayName\":\"NewName\"}",
                "{}",
                ProfileChangeStatus.PENDING,
                "PASS",
                null
        );
        ReflectionTestUtils.setField(request, "id", 2L);
        ReflectionTestUtils.setField(request, "createdAt", Instant.parse("2026-03-19T07:00:00Z"));

        given(userAccountRepository.findByIdIn(List.of("user-9"))).willReturn(List.of());

        var response = repository.getProfileReviewSummaries(List.of(request));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).username()).isEqualTo("user-9");
        assertThat(response.get(0).currentDisplayName()).isNull();
        assertThat(response.get(0).requestedDisplayName()).isEqualTo("NewName");
    }

    @Test
    void getProfileReviewSummaries_toleratesInvalidSnapshotJson() {
        ProfileChangeRequest request = new ProfileChangeRequest(
                "user-2",
                "{invalid",
                "{also-invalid",
                ProfileChangeStatus.PENDING,
                "PASS",
                null
        );
        ReflectionTestUtils.setField(request, "id", 3L);
        ReflectionTestUtils.setField(request, "createdAt", Instant.parse("2026-03-19T08:00:00Z"));

        UserAccount submitter = new UserAccount("user-2", "Current Name", "user2@example.com", null);
        given(userAccountRepository.findByIdIn(List.of("user-2"))).willReturn(List.of(submitter));

        var response = repository.getProfileReviewSummaries(List.of(request));

        assertThat(response).hasSize(1);
        assertThat(response.get(0).username()).isEqualTo("Current Name");
        assertThat(response.get(0).currentDisplayName()).isEqualTo("Current Name");
        assertThat(response.get(0).requestedDisplayName()).isNull();
    }
}
