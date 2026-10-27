package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.repository.SkillSuiteReferenceQueryRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Adds viewer-specific Suite membership to one page, without per-card queries or search-index state. */
@Service
public class SkillSuiteMembershipProjectionService {
    private final SkillSuiteReferenceQueryRepository references;
    private final NamespaceMemberRepository namespaceMembers;
    private final RbacService rbacService;

    public SkillSuiteMembershipProjectionService(SkillSuiteReferenceQueryRepository references,
                                                NamespaceMemberRepository namespaceMembers,
                                                RbacService rbacService) {
        this.references = references;
        this.namespaceMembers = namespaceMembers;
        this.rbacService = rbacService;
    }

    public List<SkillSummaryResponse> enrich(List<SkillSummaryResponse> summaries, String userId) {
        if (summaries.isEmpty()) {
            return summaries;
        }
        Map<Long, NamespaceRole> roles = userId == null ? Map.of()
                : namespaceMembers.findByUserId(userId).stream().collect(Collectors.toMap(
                        NamespaceMember::getNamespaceId, NamespaceMember::getRole));
        Set<String> platformRoles = userId == null ? Set.of() : rbacService.getUserRoleCodes(userId);
        Set<Long> related = references.findSkillIdsWithVisibleMemberships(
                summaries.stream().map(SkillSummaryResponse::id).distinct().toList(),
                userId, roles, platformRoles);
        return summaries.stream().map(summary -> summary.withVisibleSuites(related.contains(summary.id()))).toList();
    }
}
