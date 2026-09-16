package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.review.ReviewPermissionChecker;
import com.iflytek.skillhub.domain.review.ReviewSubjectType;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves viewer-specific Suite details while keeping persisted lifecycle and live availability separate. */
@Service
public class SkillSuiteQueryService {

    private final NamespaceRepository namespaceRepository;
    private final SkillSuiteRepository suiteRepository;
    private final SkillSuiteVersionRepository versionRepository;
    private final SkillSuiteVersionMemberRepository memberRepository;
    private final SkillSuiteMemberStateResolver stateResolver;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewPermissionChecker reviewPermissionChecker;

    public SkillSuiteQueryService(
            NamespaceRepository namespaceRepository,
            SkillSuiteRepository suiteRepository,
            SkillSuiteVersionRepository versionRepository,
            SkillSuiteVersionMemberRepository memberRepository,
            SkillSuiteMemberStateResolver stateResolver,
            ReviewTaskRepository reviewTaskRepository,
            ReviewPermissionChecker reviewPermissionChecker
    ) {
        this.namespaceRepository = namespaceRepository;
        this.suiteRepository = suiteRepository;
        this.versionRepository = versionRepository;
        this.memberRepository = memberRepository;
        this.stateResolver = stateResolver;
        this.reviewTaskRepository = reviewTaskRepository;
        this.reviewPermissionChecker = reviewPermissionChecker;
    }

    @Transactional(readOnly = true)
    public Detail getDetail(
            String namespaceSlug,
            String suiteSlug,
            String requestedVersion,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceSlug));
        SkillSuite suite = suiteRepository.findByNamespaceIdAndSlug(namespace.getId(), suiteSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteSlug));
        SkillSuiteVersion version = resolveVersion(suite, requestedVersion);
        return assembleDetail(namespace, suite, version, userId, namespaceRoles, platformRoles);
    }

    /** Resolves the immutable Suite version captured by an earlier idempotent install operation. */
    @Transactional(readOnly = true)
    public Detail getDetailByVersionId(
            String namespaceSlug,
            String suiteSlug,
            Long versionId,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceSlug));
        SkillSuite suite = suiteRepository.findByNamespaceIdAndSlug(namespace.getId(), suiteSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteSlug));
        SkillSuiteVersion version = versionRepository.findById(versionId)
                .filter(candidate -> suite.getId().equals(candidate.getSuiteId()))
                .orElseThrow(() -> new DomainNotFoundException("error.suite.version.notFound", versionId));
        return assembleDetail(namespace, suite, version, userId, namespaceRoles, platformRoles);
    }

    private Detail assembleDetail(
            Namespace namespace,
            SkillSuite suite,
            SkillSuiteVersion version,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (!canRead(namespace, suite, version, userId, namespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("error.suite.access.denied");
        }

        List<SkillSuiteVersionMember> snapshots =
                memberRepository.findBySuiteVersionIdOrderByPosition(version.getId());
        List<SkillSuiteMemberState> states = stateResolver.resolveForViewer(
                snapshots, userId, namespaceRoles, platformRoles);
        List<MemberDetail> memberDetails = new ArrayList<>(snapshots.size());
        for (int index = 0; index < snapshots.size(); index++) {
            SkillSuiteMemberAvailability availability = new SkillSuiteMemberEligibilityPolicy().evaluate(
                    suite.getNamespaceId(), version.getVisibility(), states.get(index));
            memberDetails.add(new MemberDetail(snapshots.get(index), states.get(index), availability));
        }
        boolean available = version.getStatus() == SkillSuiteVersionStatus.PUBLISHED
                && suite.getStatus() == SkillSuiteStatus.ACTIVE
                && !suite.isHidden()
                && memberDetails.stream().allMatch(member -> member.availability().available());
        return new Detail(namespace, suite, version, available, memberDetails);
    }

    @Transactional(readOnly = true)
    public List<VersionSummary> listVersions(
            String namespaceSlug,
            String suiteSlug,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", namespaceSlug));
        SkillSuite suite = suiteRepository.findByNamespaceIdAndSlug(namespace.getId(), suiteSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.suite.notFound", suiteSlug));
        return versionRepository.findBySuiteId(suite.getId()).stream()
                .filter(version -> canRead(namespace, suite, version, userId, namespaceRoles, platformRoles))
                .sorted(Comparator.comparing(
                        SkillSuiteVersion::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(version -> new VersionSummary(
                        version.getId(), version.getVersion(), version.getStatus(),
                        version.getVisibility(), version.getPublishedAt(),
                        version.getYankedAt(), version.getCreatedAt()))
                .toList();
    }

    private SkillSuiteVersion resolveVersion(SkillSuite suite, String requestedVersion) {
        if (requestedVersion != null && !requestedVersion.isBlank()) {
            return versionRepository.findBySuiteIdAndVersion(suite.getId(), requestedVersion)
                    .orElseThrow(() -> new DomainNotFoundException(
                            "error.suite.version.notFound", requestedVersion));
        }
        if (suite.getLatestVersionId() == null) {
            throw new DomainNotFoundException("error.suite.version.notFound", "latest");
        }
        return versionRepository.findById(suite.getLatestVersionId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "error.suite.version.notFound", suite.getLatestVersionId()));
    }

    private boolean canRead(
            Namespace namespace,
            SkillSuite suite,
            SkillSuiteVersion version,
            String userId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) {
        if (platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }
        NamespaceRole role = namespaceRoles.get(suite.getNamespaceId());
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED && role == null) {
            return false;
        }
        boolean namespaceAdmin = role == NamespaceRole.OWNER || role == NamespaceRole.ADMIN;
        boolean currentCreator = userId != null && userId.equals(suite.getCreatedBy()) && role != null;

        if (version.getStatus() == SkillSuiteVersionStatus.PENDING_REVIEW) {
            return reviewTaskRepository.findBySubjectTypeAndSubjectVersionIdAndStatus(
                            ReviewSubjectType.SUITE_VERSION, version.getId(), ReviewTaskStatus.PENDING)
                    .map(task -> reviewPermissionChecker.canReadReview(
                            task, userId, namespace.getType(), namespaceRoles, platformRoles))
                    .orElse(namespaceAdmin || platformRoles.contains("SKILL_ADMIN"));
        }
        if (version.getStatus() != SkillSuiteVersionStatus.PUBLISHED
                && version.getStatus() != SkillSuiteVersionStatus.YANKED) {
            if (namespaceAdmin || currentCreator || platformRoles.contains("SKILL_ADMIN")) {
                return true;
            }
            return false;
        }
        if (suite.getStatus() != SkillSuiteStatus.ACTIVE || suite.isHidden()) {
            if (namespaceAdmin || currentCreator) {
                return true;
            }
            return false;
        }
        if (version.getVisibility() == SkillVisibility.PUBLIC) {
            return true;
        }
        if (version.getVisibility() == SkillVisibility.NAMESPACE_ONLY && role != null) {
            return true;
        }
        if (version.getVisibility() == SkillVisibility.PRIVATE && (namespaceAdmin || currentCreator)) {
            return true;
        }
        return false;
    }

    public record MemberDetail(
            SkillSuiteVersionMember snapshot,
            SkillSuiteMemberState state,
            SkillSuiteMemberAvailability availability
    ) {
    }

    public record Detail(
            Namespace namespace,
            SkillSuite suite,
            SkillSuiteVersion version,
            boolean available,
            List<MemberDetail> members
    ) {
        public Detail {
            members = List.copyOf(members);
        }
    }

    public record VersionSummary(
            Long id,
            String version,
            SkillSuiteVersionStatus status,
            SkillVisibility visibility,
            java.time.Instant publishedAt,
            java.time.Instant yankedAt,
            java.time.Instant createdAt
    ) {
    }
}
