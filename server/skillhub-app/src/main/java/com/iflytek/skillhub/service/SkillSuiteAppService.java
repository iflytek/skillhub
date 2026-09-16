package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.audit.AuditDetail;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.LocalizedDomainException;
import com.iflytek.skillhub.domain.skill.service.SkillQueryService;
import com.iflytek.skillhub.domain.suite.CreateSkillSuiteDraftCommand;
import com.iflytek.skillhub.domain.suite.SkillSuiteActionContext;
import com.iflytek.skillhub.domain.suite.SkillSuiteDraftService;
import com.iflytek.skillhub.domain.suite.SkillSuiteLifecycleService;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallMetricsService;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallOperation;
import com.iflytek.skillhub.domain.suite.SkillSuiteInstallOperationRepository;
import com.iflytek.skillhub.domain.suite.SkillSuiteAllowedAction;
import com.iflytek.skillhub.domain.suite.SkillSuiteMemberSelection;
import com.iflytek.skillhub.domain.suite.SkillSuiteQueryService;
import com.iflytek.skillhub.domain.suite.SkillSuiteVersionMember;
import com.iflytek.skillhub.dto.SkillSuiteCreateRequest;
import com.iflytek.skillhub.dto.SkillSuiteMemberRequest;
import com.iflytek.skillhub.dto.SkillSuiteMemberResponse;
import com.iflytek.skillhub.dto.SkillSuiteResponse;
import com.iflytek.skillhub.dto.SkillSuiteInstallMemberResponse;
import com.iflytek.skillhub.dto.SkillSuiteInstallPlanResponse;
import com.iflytek.skillhub.dto.SkillSuiteMemberCandidateResponse;
import com.iflytek.skillhub.dto.SkillSuiteVersionSummaryResponse;
import com.iflytek.skillhub.dto.SkillSuiteReferenceResponse;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import com.iflytek.skillhub.repository.SkillSuiteCandidateQueryRepository;
import com.iflytek.skillhub.repository.SkillSuiteReferenceQueryRepository;
import com.iflytek.skillhub.repository.MySkillSuiteQueryRepository;
import com.iflytek.skillhub.dto.MySkillSuiteSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Application boundary for resolving Suite inputs and invoking domain workflows. */
@Service
public class SkillSuiteAppService {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteAppService.class);

    private final NamespaceRepository namespaceRepository;
    private final SkillQueryService skillQueryService;
    private final SkillSuiteDraftService draftService;
    private final SkillSuiteLifecycleService lifecycleService;
    private final SkillSuiteQueryService queryService;
    private final SkillSuiteInstallMetricsService installMetricsService;
    private final SkillSuiteInstallOperationRepository installOperationRepository;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final SkillSuiteCandidateQueryRepository candidateQueryRepository;
    private final MySkillSuiteQueryRepository mySkillSuiteQueryRepository;
    private final SkillSuiteReferenceQueryRepository referenceQueryRepository;

    public SkillSuiteAppService(
            NamespaceRepository namespaceRepository,
            SkillQueryService skillQueryService,
            SkillSuiteDraftService draftService,
            SkillSuiteLifecycleService lifecycleService,
            SkillSuiteQueryService queryService,
            SkillSuiteInstallMetricsService installMetricsService,
            SkillSuiteInstallOperationRepository installOperationRepository,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            SkillSuiteCandidateQueryRepository candidateQueryRepository,
            MySkillSuiteQueryRepository mySkillSuiteQueryRepository,
            SkillSuiteReferenceQueryRepository referenceQueryRepository
    ) {
        this.namespaceRepository = namespaceRepository;
        this.skillQueryService = skillQueryService;
        this.draftService = draftService;
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
        this.installMetricsService = installMetricsService;
        this.installOperationRepository = installOperationRepository;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.candidateQueryRepository = candidateQueryRepository;
        this.mySkillSuiteQueryRepository = mySkillSuiteQueryRepository;
        this.referenceQueryRepository = referenceQueryRepository;
    }

    public PageResponse<MySkillSuiteSummaryResponse> listMine(
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            String query,
            int page,
            int size
    ) {
        Set<Long> adminNamespaceIds = namespaceRoles.entrySet().stream()
                .filter(entry -> entry.getValue() == NamespaceRole.OWNER
                        || entry.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return mySkillSuiteQueryRepository.findMine(
                userId, namespaceRoles.keySet(), adminNamespaceIds, query,
                Math.max(0, page), Math.min(Math.max(1, size), 100));
    }

    public List<SkillSuiteMemberCandidateResponse> searchCandidates(
            String suiteNamespace,
            SkillVisibility visibility,
            String query,
            int size,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        Namespace namespace = namespaceRepository.findBySlug(suiteNamespace)
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.namespace.slug.notFound", suiteNamespace));
        if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
            throw new DomainBadRequestException("error.suite.namespace.notWritable", namespace.getStatus());
        }
        boolean superAdmin = platformRoles.contains("SUPER_ADMIN");
        if (!superAdmin && !namespaceRoles.containsKey(namespace.getId())) {
            throw new DomainForbiddenException("error.suite.lifecycle.noPermission");
        }
        int boundedSize = Math.max(1, Math.min(size, 100));
        List<Long> memberNamespaceIds = List.copyOf(namespaceRoles.keySet());
        List<Long> adminNamespaceIds = namespaceRoles.entrySet().stream()
                .filter(entry -> entry.getValue() == NamespaceRole.OWNER
                        || entry.getValue() == NamespaceRole.ADMIN)
                .map(Map.Entry::getKey)
                .toList();
        return candidateQueryRepository.search(
                namespace.getId(), visibility, query, userId, memberNamespaceIds,
                adminNamespaceIds, superAdmin, boundedSize);
    }

    public List<SkillSuiteReferenceResponse> findVisibleEntryReferences(
            Long skillId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        return referenceQueryRepository.findVisibleEntryReferences(
                skillId, userId, namespaceRoles, platformRoles);
    }

    @Transactional
    public SkillSuiteInstallPlanResponse createInstallPlan(
            String namespace,
            String slug,
            String version,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            String clientRequestId,
            HttpServletRequest request
    ) {
        String retryKey = normalizeClientRequestId(clientRequestId);
        String actorKey = idempotencyActorKey(userId, namespace, slug, request);
        SkillSuiteInstallOperation existing = installOperationRepository
                .findByClientRequestIdAndActorKey(retryKey, actorKey)
                .orElse(null);
        if (existing != null) {
            return replayInstallPlan(
                    existing, namespace, slug, version, userId, namespaceRoles, platformRoles);
        }

        SkillSuiteQueryService.Detail detail = queryService.getDetail(
                namespace, slug, version, userId, namespaceRoles, platformRoles);
        if (!detail.available()) {
            throw new DomainBadRequestException("error.suite.install.unavailable");
        }
        List<SkillSuiteInstallMemberResponse> members = resolveInstallMembers(
                detail, userId, namespaceRoles, platformRoles);

        String operationId = UUID.randomUUID().toString();
        String fingerprint = suiteFingerprint(detail, members);
        int inserted = installOperationRepository.insertIfAbsent(
                operationId, retryKey, actorKey, detail.suite().getId(), detail.version().getId());
        if (inserted == 0) {
            SkillSuiteInstallOperation concurrent = installOperationRepository
                    .findByClientRequestIdAndActorKey(retryKey, actorKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Suite install operation disappeared after an idempotency conflict"));
            return replayInstallPlan(
                    concurrent, namespace, slug, version, userId, namespaceRoles, platformRoles);
        }

        installMetricsService.recordIssuedPlan(detail.suite().getId());
        AuditRequestContext audit = AuditRequestContext.from(request);
        for (SkillSuiteQueryService.MemberDetail member : detail.members()) {
            auditLogService.record(
                    userId, "ISSUE_SKILL_DOWNLOAD", "SKILL_VERSION",
                    member.snapshot().getSkillVersionId(), operationId, audit.clientIp(), audit.userAgent(),
                    AuditDetail.builder()
                            .put("source", "SUITE")
                            .put("suiteId", detail.suite().getId())
                            .put("suiteVersionId", detail.version().getId())
                            .build());
        }
        auditLogService.record(
                userId, "ISSUE_SKILL_SUITE_INSTALL_PLAN", "SKILL_SUITE_VERSION",
                detail.version().getId(), operationId, audit.clientIp(), audit.userAgent(),
                AuditDetail.builder()
                        .put("suiteId", detail.suite().getId())
                        .put("memberCount", members.size())
                        .put("source", "SUITE")
                        .build());
        log.info(
                "Suite install plan issued [suiteId={}, versionId={}, actorId={}, memberCount={}, operationId={}]",
                detail.suite().getId(), detail.version().getId(), userId, members.size(), operationId);
        return new SkillSuiteInstallPlanResponse(
                operationId, namespace, slug, detail.version().getVersion(), fingerprint, members);
    }

    private SkillSuiteInstallPlanResponse replayInstallPlan(
            SkillSuiteInstallOperation existing,
            String namespace,
            String slug,
            String requestedVersion,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuiteQueryService.Detail detail;
        try {
            detail = queryService.getDetailByVersionId(
                    namespace, slug, existing.getSuiteVersionId(), userId, namespaceRoles, platformRoles);
        } catch (com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException exception) {
            throw new DomainBadRequestException("error.suite.install.operationConflict");
        }
        if (!existing.getSuiteId().equals(detail.suite().getId())
                || (requestedVersion != null && !requestedVersion.isBlank()
                && !requestedVersion.equals(detail.version().getVersion()))) {
            throw new DomainBadRequestException("error.suite.install.operationConflict");
        }
        if (!detail.available()) {
            throw new DomainBadRequestException("error.suite.install.unavailable");
        }
        List<SkillSuiteInstallMemberResponse> members = resolveInstallMembers(
                detail, userId, namespaceRoles, platformRoles);
        String fingerprint = suiteFingerprint(detail, members);
        log.info(
                "Suite install plan safely replayed [suiteId={}, versionId={}, actorId={}, memberCount={}, operationId={}]",
                detail.suite().getId(), detail.version().getId(), userId, members.size(), existing.getOperationId());
        return new SkillSuiteInstallPlanResponse(
                existing.getOperationId(), namespace, slug, detail.version().getVersion(), fingerprint, members);
    }

    private List<SkillSuiteInstallMemberResponse> resolveInstallMembers(
            SkillSuiteQueryService.Detail detail,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        List<SkillSuiteInstallMemberResponse> members = new ArrayList<>(detail.members().size());
        try {
            for (SkillSuiteQueryService.MemberDetail member : detail.members()) {
                var snapshot = member.snapshot();
                SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersionById(
                        snapshot.getSkillVersionId(), userId, namespaceRoles, platformRoles);
                if (!Objects.equals(resolved.namespace(), snapshot.getNamespaceSlugSnapshot())
                        || !Objects.equals(resolved.slug(), snapshot.getSkillSlugSnapshot())
                        || !Objects.equals(resolved.version(), snapshot.getSkillVersionSnapshot())
                        || !Objects.equals(resolved.fingerprint(), snapshot.getFingerprintSnapshot())) {
                    throw new DomainBadRequestException("error.suite.install.unavailable");
                }
                members.add(new SkillSuiteInstallMemberResponse(
                        snapshot.getSkillId(), snapshot.getSkillVersionId(),
                        resolved.namespace(), resolved.slug(), resolved.version(), resolved.fingerprint(),
                        resolved.downloadUrl(), snapshot.getPosition(), snapshot.isEntry()));
            }
        } catch (LocalizedDomainException exception) {
            // A Suite reader may no longer be allowed to inspect a restricted member. Do not expose
            // the member coordinate or the underlying authorization failure through install preflight.
            throw new DomainBadRequestException("error.suite.install.unavailable");
        }
        return members;
    }

    private String normalizeClientRequestId(String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (!RequestIdAccessor.isValid(clientRequestId)) {
            throw new DomainBadRequestException("error.suite.install.idempotencyKey.invalid");
        }
        return clientRequestId;
    }

    private String idempotencyActorKey(
            String userId,
            String namespace,
            String slug,
            HttpServletRequest request
    ) {
        if (userId != null) {
            return "user:" + userId;
        }
        AuditRequestContext context = AuditRequestContext.from(request);
        String callerScope = Objects.toString(context.clientIp(), "") + '\0'
                + Objects.toString(context.userAgent(), "") + '\0'
                + namespace + '/' + slug;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "anonymous:" + HexFormat.of().formatHex(
                    digest.digest(callerScope.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to scope anonymous Suite idempotency", exception);
        }
    }

    public SkillSuiteResponse getDetail(
            String namespace,
            String slug,
            String version,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillSuiteQueryService.Detail detail = queryService.getDetail(
                namespace, slug, version, userId, namespaceRoles, platformRoles);
        List<SkillSuiteMemberResponse> members = detail.members().stream()
                .map(member -> new SkillSuiteMemberResponse(
                        member.snapshot().getSkillId(), member.snapshot().getSkillVersionId(),
                        member.snapshot().getNamespaceSlugSnapshot(), member.snapshot().getSkillSlugSnapshot(),
                        member.state().viewerCanRead() ? member.state().displayName() : null,
                        member.state().viewerCanRead() ? member.state().summary() : null,
                        member.snapshot().getSkillVersionSnapshot(), member.snapshot().getFingerprintSnapshot(),
                        member.snapshot().getPosition(), member.snapshot().isEntry(),
                        member.state().viewerCanRead(),
                        member.availability().reason() == null ? null : member.availability().reason().name()))
                .toList();
        Set<SkillSuiteAllowedAction> allowedActions =
                lifecycleService.allowedActions(
                        detail.suite(), detail.version(), detail.namespace(),
                        authorizationContext(userId, namespaceRoles, platformRoles));
        return new SkillSuiteResponse(
                detail.suite().getId(), detail.version().getId(), detail.namespace().getSlug(),
                detail.suite().getSlug(), detail.version().getDisplayName(), detail.version().getSummary(),
                detail.version().getOverview(),
                detail.version().getVersion(), detail.version().getStatus().name(),
                detail.version().getVisibility(), detail.suite().getStatus().name(),
                detail.suite().isHidden(), allowedActions, detail.available(), members);
    }

    public SkillSuiteResponse create(
            SkillSuiteCreateRequest request,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest httpRequest
    ) {
        Namespace namespace = namespaceRepository.findBySlug(request.namespace())
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.namespace.slug.notFound", request.namespace()));
        SkillSuiteDraftService.CreatedDraft created = draftService.create(
                toCommand(namespace.getId(), request, userId, namespaceRoles, platformRoles),
                context(userId, namespaceRoles, platformRoles, httpRequest));
        return toResponse(namespace, created, userId, namespaceRoles, platformRoles);
    }

    public SkillSuiteResponse createVersion(
            Long suiteId,
            SkillSuiteCreateRequest request,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest httpRequest
    ) {
        Namespace namespace = namespaceRepository.findBySlug(request.namespace())
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.namespace.slug.notFound", request.namespace()));
        SkillSuiteDraftService.CreatedDraft created = draftService.createVersion(
                suiteId, toCommand(namespace.getId(), request, userId, namespaceRoles, platformRoles),
                context(userId, namespaceRoles, platformRoles, httpRequest));
        return toResponse(namespace, created, userId, namespaceRoles, platformRoles);
    }

    public SkillSuiteResponse updateDraft(
            Long suiteId,
            Long versionId,
            SkillSuiteCreateRequest request,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest httpRequest
    ) {
        Namespace namespace = namespaceRepository.findBySlug(request.namespace())
                .orElseThrow(() -> new DomainBadRequestException(
                        "error.namespace.slug.notFound", request.namespace()));
        SkillSuiteDraftService.CreatedDraft updated = draftService.updateDraft(
                suiteId, versionId, toCommand(namespace.getId(), request, userId, namespaceRoles, platformRoles),
                context(userId, namespaceRoles, platformRoles, httpRequest));
        return toResponse(namespace, updated, userId, namespaceRoles, platformRoles);
    }

    public List<SkillSuiteVersionSummaryResponse> listVersions(
            String namespace,
            String slug,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        return queryService.listVersions(namespace, slug, userId, namespaceRoles, platformRoles).stream()
                .map(version -> new SkillSuiteVersionSummaryResponse(
                        version.id(), version.version(), version.status().name(), version.visibility(),
                        version.publishedAt(), version.yankedAt(), version.createdAt()))
                .toList();
    }

    public void submitForReview(
            Long suiteId,
            Long versionId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.submitForReview(
                suiteId, versionId, context(userId, namespaceRoles, platformRoles, request));
    }

    public void publishPrivate(
            Long suiteId,
            Long versionId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.confirmPrivatePublish(
                suiteId, versionId, context(userId, namespaceRoles, platformRoles, request));
    }

    public void approve(
            Long reviewTaskId,
            String comment,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.approveReview(
                reviewTaskId, comment, context(userId, namespaceRoles, platformRoles, request));
    }

    public void reject(
            Long reviewTaskId,
            String comment,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.rejectReview(
                reviewTaskId, comment, context(userId, namespaceRoles, platformRoles, request));
    }

    public void reopen(
            Long suiteId,
            Long versionId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.reopenRejected(
                suiteId, versionId, context(userId, namespaceRoles, platformRoles, request));
    }

    public void yank(
            Long suiteId,
            Long versionId,
            String reason,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.yank(
                suiteId, versionId, reason,
                context(userId, namespaceRoles, platformRoles, request));
    }

    public void setHidden(
            Long suiteId,
            boolean hidden,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.setHidden(
                suiteId, hidden, context(userId, namespaceRoles, platformRoles, request));
    }

    public void setArchived(
            Long suiteId,
            boolean archived,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.setArchived(
                suiteId, archived, context(userId, namespaceRoles, platformRoles, request));
    }

    public void delete(
            Long suiteId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        lifecycleService.delete(
                suiteId, context(userId, namespaceRoles, platformRoles, request));
    }

    private SkillSuiteMemberSelection resolve(
            SkillSuiteMemberRequest member,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        SkillQueryService.ResolvedVersionDTO resolved = skillQueryService.resolveVersionById(
                member.skillVersionId(), userId, namespaceRoles, platformRoles);
        if (!Objects.equals(resolved.namespace(), member.namespace())
                || !Objects.equals(resolved.slug(), member.slug())
                || !Objects.equals(resolved.version(), member.version())) {
            throw new DomainBadRequestException("error.suite.members.selectionMismatch");
        }
        return new SkillSuiteMemberSelection(
                resolved.skillId(), resolved.versionId(), resolved.namespace(), resolved.slug(),
                resolved.version(), resolved.fingerprint());
    }

    private CreateSkillSuiteDraftCommand toCommand(
            Long namespaceId,
            SkillSuiteCreateRequest request,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        List<SkillSuiteMemberSelection> selections = new ArrayList<>(request.members().size());
        List<String> invalidMembers = new ArrayList<>();
        for (SkillSuiteMemberRequest member : request.members()) {
            try {
                selections.add(resolve(member, userId, namespaceRoles, platformRoles));
            } catch (LocalizedDomainException exception) {
                invalidMembers.add(String.format(
                        "@%s/%s@%s (%s)", member.namespace(), member.slug(), member.version(),
                        exception.messageCode()));
            }
        }
        if (!invalidMembers.isEmpty()) {
            throw new DomainBadRequestException(
                    "error.suite.members.invalid", String.join("; ", invalidMembers));
        }
        Long entryVersionId = resolveEntryVersionId(request.entrySkill(), selections);
        return new CreateSkillSuiteDraftCommand(
                namespaceId, request.slug(), request.displayName(), request.summary(), request.overview(),
                request.version(), request.visibility(), request.changelog(),
                entryVersionId, selections);
    }

    private Long resolveEntryVersionId(
            SkillSuiteMemberRequest entry,
            List<SkillSuiteMemberSelection> members
    ) {
        if (entry == null) {
            throw new DomainBadRequestException("error.suite.entry.required");
        }
        return members.stream()
                .filter(member -> Objects.equals(member.skillVersionId(), entry.skillVersionId())
                        && Objects.equals(member.namespaceSlug(), entry.namespace())
                        && Objects.equals(member.skillSlug(), entry.slug())
                        && Objects.equals(member.version(), entry.version()))
                .map(SkillSuiteMemberSelection::skillVersionId)
                .findFirst()
                .orElseThrow(() -> new DomainBadRequestException("error.suite.entry.notMember"));
    }

    private SkillSuiteActionContext context(
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles,
            HttpServletRequest request
    ) {
        AuditRequestContext audit = AuditRequestContext.from(request);
        return new SkillSuiteActionContext(
                userId, namespaceRoles, platformRoles, requestIdAccessor.current(),
                audit.clientIp(), audit.userAgent());
    }

    private SkillSuiteResponse toResponse(
            Namespace namespace,
            SkillSuiteDraftService.CreatedDraft created,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        List<SkillSuiteMemberResponse> members = new ArrayList<>();
        for (SkillSuiteVersionMember member : created.members()) {
            members.add(new SkillSuiteMemberResponse(
                    member.getSkillId(), member.getSkillVersionId(), member.getNamespaceSlugSnapshot(),
                    member.getSkillSlugSnapshot(), null, null, member.getSkillVersionSnapshot(),
                    member.getFingerprintSnapshot(), member.getPosition(), member.isEntry(),
                    false,
                    null));
        }
        Set<SkillSuiteAllowedAction> allowedActions =
                lifecycleService.allowedActions(
                        created.suite(), created.version(), namespace,
                        authorizationContext(userId, namespaceRoles, platformRoles));
        return new SkillSuiteResponse(
                created.suite().getId(), created.version().getId(), namespace.getSlug(),
                created.suite().getSlug(), created.version().getDisplayName(), created.version().getSummary(),
                created.version().getOverview(),
                created.version().getVersion(), created.version().getStatus().name(),
                created.version().getVisibility(), created.suite().getStatus().name(),
                created.suite().isHidden(), allowedActions, false, members);
    }

    private SkillSuiteActionContext authorizationContext(
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        return new SkillSuiteActionContext(userId, namespaceRoles, platformRoles, null, null, null);
    }

    private String suiteFingerprint(
            SkillSuiteQueryService.Detail detail,
            List<SkillSuiteInstallMemberResponse> members
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("suite:" + detail.namespace().getSlug() + "/" + detail.suite().getSlug()
                    + "@" + detail.version().getVersion() + "\n").getBytes(StandardCharsets.UTF_8));
            for (SkillSuiteInstallMemberResponse member : members) {
                String line = member.position() + ":" + member.namespace() + "/" + member.slug()
                        + "@" + member.version() + ":" + member.fingerprint()
                        + ":entry=" + member.entry() + "\n";
                digest.update(line.getBytes(StandardCharsets.UTF_8));
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute Suite fingerprint", exception);
        }
    }
}
