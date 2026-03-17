package com.iflytek.skillhub.domain.skill;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VisibilityCheckerTest {

    private VisibilityChecker checker;
    private Skill publicSkill;
    private Skill namespaceOnlySkill;
    private Skill privateSkill;
    private Skill unpublishedPublicSkill;

    private static final Long NAMESPACE_ID = 1L;
    private static final String OWNER_ID = "user-100";
    private static final String OTHER_USER_ID = "user-200";
    private static final String ADMIN_USER_ID = "user-300";
    private static final String NAMESPACE_OWNER_ID = "user-400";

    @BeforeEach
    void setUp() {
        checker = new VisibilityChecker();

        publicSkill = new Skill(NAMESPACE_ID, "public-skill", OWNER_ID, SkillVisibility.PUBLIC);
        publicSkill.setLatestVersionId(10L);
        namespaceOnlySkill = new Skill(NAMESPACE_ID, "namespace-skill", OWNER_ID, SkillVisibility.NAMESPACE_ONLY);
        namespaceOnlySkill.setLatestVersionId(11L);
        privateSkill = new Skill(NAMESPACE_ID, "private-skill", OWNER_ID, SkillVisibility.PRIVATE);
        privateSkill.setLatestVersionId(12L);
        unpublishedPublicSkill = new Skill(NAMESPACE_ID, "draft-public-skill", OWNER_ID, SkillVisibility.PUBLIC);
    }

    @Test
    void testPublicSkillAccessibleByAnonymous() {
        boolean canAccess = checker.canAccess(publicSkill, null, Map.of());
        assertTrue(canAccess);
    }

    @Test
    void testPublicSkillAccessibleByAnyUser() {
        boolean canAccess = checker.canAccess(publicSkill, OTHER_USER_ID, Map.of());
        assertTrue(canAccess);
    }

    @Test
    void testNamespaceOnlySkillNotAccessibleByAnonymous() {
        boolean canAccess = checker.canAccess(namespaceOnlySkill, null, Map.of());
        assertFalse(canAccess);
    }

    @Test
    void testNamespaceOnlySkillNotAccessibleByNonMember() {
        boolean canAccess = checker.canAccess(namespaceOnlySkill, OTHER_USER_ID, Map.of());
        assertFalse(canAccess);
    }

    @Test
    void testNamespaceOnlySkillAccessibleByMember() {
        Map<Long, NamespaceRole> roles = Map.of(NAMESPACE_ID, NamespaceRole.MEMBER);
        boolean canAccess = checker.canAccess(namespaceOnlySkill, OTHER_USER_ID, roles);
        assertTrue(canAccess);
    }

    @Test
    void testPrivateSkillNotAccessibleByAnonymous() {
        boolean canAccess = checker.canAccess(privateSkill, null, Map.of());
        assertFalse(canAccess);
    }

    @Test
    void testPrivateSkillAccessibleByOwner() {
        boolean canAccess = checker.canAccess(privateSkill, OWNER_ID, Map.of());
        assertTrue(canAccess);
    }

    @Test
    void testPrivateSkillAccessibleByAdmin() {
        Map<Long, NamespaceRole> roles = Map.of(NAMESPACE_ID, NamespaceRole.ADMIN);
        boolean canAccess = checker.canAccess(privateSkill, ADMIN_USER_ID, roles);
        assertTrue(canAccess);
    }

    @Test
    void testPrivateSkillAccessibleByNamespaceOwner() {
        Map<Long, NamespaceRole> roles = Map.of(NAMESPACE_ID, NamespaceRole.OWNER);
        boolean canAccess = checker.canAccess(privateSkill, NAMESPACE_OWNER_ID, roles);
        assertTrue(canAccess);
    }

    @Test
    void testPrivateSkillNotAccessibleByRegularMember() {
        Map<Long, NamespaceRole> roles = Map.of(NAMESPACE_ID, NamespaceRole.MEMBER);
        boolean canAccess = checker.canAccess(privateSkill, OTHER_USER_ID, roles);
        assertFalse(canAccess);
    }

    @Test
    void testPrivateSkillNotAccessibleByNonMember() {
        boolean canAccess = checker.canAccess(privateSkill, OTHER_USER_ID, Map.of());
        assertFalse(canAccess);
    }

    @Test
    void testUnpublishedSkillNotAccessibleByAnonymousEvenWhenPublic() {
        boolean canAccess = checker.canAccess(unpublishedPublicSkill, null, Map.of());
        assertFalse(canAccess);
    }

    @Test
    void testUnpublishedSkillNotAccessibleByOtherUserEvenWhenPublic() {
        boolean canAccess = checker.canAccess(unpublishedPublicSkill, OTHER_USER_ID, Map.of());
        assertFalse(canAccess);
    }

    @Test
    void testUnpublishedSkillNotAccessibleByAdmin() {
        Map<Long, NamespaceRole> roles = Map.of(NAMESPACE_ID, NamespaceRole.ADMIN);
        boolean canAccess = checker.canAccess(unpublishedPublicSkill, ADMIN_USER_ID, roles);
        assertFalse(canAccess);
    }

    @Test
    void testUnpublishedSkillAccessibleByOwner() {
        boolean canAccess = checker.canAccess(unpublishedPublicSkill, OWNER_ID, Map.of());
        assertTrue(canAccess);
    }
}
