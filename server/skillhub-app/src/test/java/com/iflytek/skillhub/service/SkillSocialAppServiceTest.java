package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.social.SkillRatingService;
import com.iflytek.skillhub.domain.social.SkillStarService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillSocialAppServiceTest {
    private final SkillRepository skills = mock(SkillRepository.class);
    private final SkillStarService stars = mock(SkillStarService.class);
    private final SkillRatingService ratings = mock(SkillRatingService.class);
    private final SkillSocialAppService service = new SkillSocialAppService(skills, new VisibilityChecker(skill -> true), stars, ratings);
    private Skill skill;

    @BeforeEach
    void setup() {
        skill = new Skill(7L, "secret", "owner", SkillVisibility.PRIVATE);
        skill.setLatestVersionId(11L);
        when(skills.findById(1L)).thenReturn(Optional.of(skill));
    }

    @ParameterizedTest
    @EnumSource(SkillVisibility.class)
    void deniedAccessNeverReadsOrWritesSocialData(SkillVisibility visibility) {
        skill.setVisibility(visibility);
        if (visibility == SkillVisibility.PUBLIC) skill.setLatestVersionId(null);
        assertRejected("outsider", Map.of(), Set.of());
    }

    @Test
    void hiddenSkillDeniesOrdinaryMember() {
        skill.setVisibility(SkillVisibility.PUBLIC);
        skill.setHidden(true);
        assertRejected("member", Map.of(7L, NamespaceRole.MEMBER), Set.of());
    }

    @Test
    void missingObjectHasNoSocialSideEffects() {
        when(skills.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.star(1L, "viewer", Map.of(), Set.of())).isInstanceOf(DomainNotFoundException.class);
        verifyNoInteractions(stars, ratings);
    }

    @Test
    void legitimateRolesRetainAccess() {
        allow("owner", Map.of(), Set.of());
        allow("admin", Map.of(7L, NamespaceRole.ADMIN), Set.of());
        allow("ns-owner", Map.of(7L, NamespaceRole.OWNER), Set.of());
        allow("platform-admin", Map.of(), Set.of("SUPER_ADMIN"));
    }

    @Test
    void publishedPublicAndNamespaceMemberAccessRetained() {
        skill.setVisibility(SkillVisibility.PUBLIC);
        allow("viewer", Map.of(), Set.of());
        skill.setVisibility(SkillVisibility.NAMESPACE_ONLY);
        allow("member", Map.of(7L, NamespaceRole.MEMBER), Set.of());
    }

    private void allow(String user, Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        service.star(1L, user, roles, platformRoles);
        service.rate(1L, user, (short) 4, roles, platformRoles);
        service.isStarred(1L, user, roles, platformRoles);
        service.getUserRating(1L, user, roles, platformRoles);
        verify(stars).star(1L, user);
        verify(stars).isStarred(1L, user);
        verify(ratings).rate(1L, user, (short) 4);
        verify(ratings).getUserRating(1L, user);
    }

    private void assertRejected(String user, Map<Long, NamespaceRole> roles, Set<String> platformRoles) {
        assertThatThrownBy(() -> service.star(1L, user, roles, platformRoles)).isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> service.rate(1L, user, (short) 4, roles, platformRoles)).isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> service.isStarred(1L, user, roles, platformRoles)).isInstanceOf(DomainForbiddenException.class);
        assertThatThrownBy(() -> service.getUserRating(1L, user, roles, platformRoles)).isInstanceOf(DomainForbiddenException.class);
        verifyNoInteractions(stars, ratings);
    }
}
