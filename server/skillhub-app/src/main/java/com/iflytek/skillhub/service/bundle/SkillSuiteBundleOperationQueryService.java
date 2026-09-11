package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperation;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleExecutionOperationRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResult;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleMemberResultRepository;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundleOperationAuthorizationPolicy;
import com.iflytek.skillhub.dto.SkillSuiteBundleOperationDetailResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads one Bundle operation through an actor/governance authorization and redaction boundary. */
@Service
public class SkillSuiteBundleOperationQueryService {

    private final SkillSuiteBundleExecutionOperationRepository operationRepository;
    private final SkillSuiteBundleMemberResultRepository memberRepository;
    private final NamespaceRepository namespaceRepository;
    private final SkillRepository skillRepository;
    private final VisibilityChecker visibilityChecker;

    public SkillSuiteBundleOperationQueryService(
            SkillSuiteBundleExecutionOperationRepository operationRepository,
            SkillSuiteBundleMemberResultRepository memberRepository,
            NamespaceRepository namespaceRepository,
            SkillRepository skillRepository,
            VisibilityChecker visibilityChecker
    ) {
        this.operationRepository = operationRepository;
        this.memberRepository = memberRepository;
        this.namespaceRepository = namespaceRepository;
        this.skillRepository = skillRepository;
        this.visibilityChecker = visibilityChecker;
    }

    @Transactional(readOnly = true)
    public SkillSuiteBundleOperationDetailResponse get(
            String operationId,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuiteBundleExecutionOperation operation = operationRepository.findById(operationId)
                .orElseThrow(this::notFound);
        if (!SkillSuiteBundleOperationAuthorizationPolicy.canAccess(
                operation, actorId, namespaceRoles, platformRoles)) {
            // Do not reveal whether an operation ID exists to an unrelated caller.
            throw notFound();
        }
        var persistedMembers = memberRepository.findByOperationIdOrderByPosition(operationId);
        List<Long> skillIds = persistedMembers.stream()
                        .map(member -> member.getSkillId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        Map<Long, Skill> skillsById = (skillIds.isEmpty() ? List.<Skill>of() : skillRepository.findByIdIn(skillIds))
                .stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        List<String> namespaceSlugs = persistedMembers.stream()
                        .map(member -> member.getNamespaceSlug())
                        .distinct()
                        .toList();
        Map<String, Long> namespaceIdsBySlug = (namespaceSlugs.isEmpty()
                ? List.<com.iflytek.skillhub.domain.namespace.Namespace>of()
                : namespaceRepository.findBySlugIn(namespaceSlugs)).stream()
                .collect(Collectors.toMap(namespace -> namespace.getSlug(), namespace -> namespace.getId()));
        var members = persistedMembers.stream()
                .map(member -> canReadMember(
                        member.getSkillId() == null ? null : skillsById.get(member.getSkillId()),
                        namespaceIdsBySlug.get(member.getNamespaceSlug()), member.getRequestedVisibility(),
                        operation, actorId, namespaceRoles, platformRoles)
                        ? visibleMember(member)
                        : redactedMember(member))
                .toList();
        String namespaceSlug = namespaceRepository.findById(operation.getNamespaceId())
                .map(namespace -> namespace.getSlug())
                .orElseThrow(this::notFound);
        return new SkillSuiteBundleOperationDetailResponse(
                operation.getOperationId(), operation.getStatus(), operation.getMode(),
                "@" + namespaceSlug + "/" + operation.getTargetSuiteSlug(), operation.getNamespaceId(),
                operation.getTargetSuiteId(), operation.getTargetVersion(),
                operation.getFailureCode(), operation.getResultSuiteId(), operation.getResultSuiteVersionId(),
                operation.getCreatedAt(), operation.getUpdatedAt(), operation.getCompletedAt(), members);
    }

    private boolean canReadMember(
            Skill skill,
            Long namespaceId,
            SkillVisibility visibility,
            SkillSuiteBundleExecutionOperation operation,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (skill != null) {
            return visibilityChecker.canAccess(skill, actorId, namespaceRoles, platformRoles);
        }
        if (platformRoles.contains("SUPER_ADMIN") || visibility == SkillVisibility.PUBLIC) {
            return true;
        }
        NamespaceRole role = namespaceId == null ? null : namespaceRoles.get(namespaceId);
        if (visibility == SkillVisibility.NAMESPACE_ONLY) {
            return role != null;
        }
        return operation.getActorId().equals(actorId)
                || role == NamespaceRole.OWNER
                || role == NamespaceRole.ADMIN;
    }

    private SkillSuiteBundleOperationDetailResponse.OperationMember visibleMember(
            SkillSuiteBundleMemberResult member
    ) {
        return new SkillSuiteBundleOperationDetailResponse.OperationMember(
                member.getPosition(), false, "@" + member.getNamespaceSlug() + "/" + member.getSkillSlug(),
                member.getSourceType(), member.getRelationshipChange(), member.getPublishAction(),
                member.getStatus(), member.getRequestedVisibility(), member.getRequestedVersion(),
                member.getSkillId(), member.getSkillVersionId(), member.getErrors(), member.getWarnings());
    }

    private SkillSuiteBundleOperationDetailResponse.OperationMember redactedMember(
            SkillSuiteBundleMemberResult member
    ) {
        return new SkillSuiteBundleOperationDetailResponse.OperationMember(
                member.getPosition(), true, null, null, null, null, member.getStatus(),
                null, null, null, null, List.of(), List.of());
    }

    private DomainNotFoundException notFound() {
        return new DomainNotFoundException("error.suite.bundle.operation.notFound");
    }
}
