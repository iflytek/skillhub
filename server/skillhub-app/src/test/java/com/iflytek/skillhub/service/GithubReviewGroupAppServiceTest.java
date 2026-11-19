package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.ReviewScopeResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.GithubReviewGroupQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GithubReviewGroupAppServiceTest {
    private final ReviewPortalAppService portal = mock(ReviewPortalAppService.class);
    private final GithubReviewGroupQueryRepository repository = mock(GithubReviewGroupQueryRepository.class);
    private final GithubReviewGroupAppService service = new GithubReviewGroupAppService(portal, repository);

    @Test
    void refusesUnauthorizedExplicitScopeBeforeReadingAnyTasks() {
        when(portal.listReviewScopes("reviewer", Map.of())).thenReturn(List.of(new ReviewScopeResponse(10L, "team", "Team")));
        assertThatThrownBy(() -> service.list("PENDING", 20L, "", null, 0, 20, "DESC", "reviewer", Map.of()))
                .isInstanceOf(DomainForbiddenException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void validatesLimitsAndFiltersBeforeQuerying() {
        assertThatThrownBy(() -> service.list("PENDING", null, "", null, 0, 51, "DESC", "reviewer", Map.of()))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.list("INVALID", null, "", null, 0, 20, "DESC", "reviewer", Map.of()))
                .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.members(-1, "PENDING", null, "", null, 0, 20, "DESC", "reviewer", Map.of()))
                .isInstanceOf(DomainBadRequestException.class);
        verifyNoInteractions(repository, portal);
    }

    @Test
    void bindsOnlyCurrentAuthorizedScopesAndDoesNotInferSuccessForEmptyGroups() {
        when(portal.listReviewScopes("reviewer", Map.of())).thenReturn(List.of(new ReviewScopeResponse(10L, "team", "Team")));
        when(repository.members(any(), eq(Set.of(10L)), eq(42L), eq(0), eq(20), eq(false)))
                .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));
        assertThat(service.members(42, "ALL", null, "", null, 0, 20, "DESC", "reviewer", Map.of()).items()).isEmpty();
        verify(repository).members(argThat(filter -> filter.status() == null && filter.originType().equals("GITHUB_SYNC")),
                eq(Set.of(10L)), eq(42L), eq(0), eq(20), eq(false));
    }
}
