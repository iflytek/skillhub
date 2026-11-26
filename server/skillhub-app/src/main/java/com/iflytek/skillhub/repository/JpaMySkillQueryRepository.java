package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.review.PromotionRequestRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillOwnerAccess;
import com.iflytek.skillhub.domain.skill.SkillStatus;
import com.iflytek.skillhub.domain.skill.service.SkillLifecycleProjectionService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.SkillLifecycleVersionResponse;
import com.iflytek.skillhub.dto.SkillSummaryResponse;
import com.iflytek.skillhub.service.SkillAvatarUrls;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

@Repository
public class JpaMySkillQueryRepository implements MySkillQueryRepository {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final NamespaceRepository namespaceRepository;
    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillLifecycleProjectionService skillLifecycleProjectionService;
    private final UserAccountRepository userAccountRepository;
    private final SkillOwnerAccess ownerAccess;

    @Autowired
    public JpaMySkillQueryRepository(NamespaceRepository namespaceRepository,
                                     PromotionRequestRepository promotionRequestRepository,
                                     SkillLifecycleProjectionService skillLifecycleProjectionService,
                                     UserAccountRepository userAccountRepository,
                                     SkillOwnerAccess ownerAccess) {
        this.namespaceRepository = namespaceRepository;
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillLifecycleProjectionService = skillLifecycleProjectionService;
        this.userAccountRepository = userAccountRepository;
        this.ownerAccess = ownerAccess;
    }

    /** Internal work such as pending reviews is listed only while the owner may read it as the owner. */
    private static final String OWNER_RETAINS_ACCESS = """
            (n.type <> 'TEAM'
             OR EXISTS (SELECT 1 FROM namespace_member m
                        WHERE m.namespace_id = s.namespace_id AND m.user_id = s.owner_id))
            """;

    /**
     * Owned skills the owner may still read. In a TEAM namespace the owner has left, only active public
     * published skills remain, the same ones anyone can read.
     */
    private static final String OWNED_READABLE = "(" + OWNER_RETAINS_ACCESS
            + " OR (s.visibility = 'PUBLIC' AND s.status = 'ACTIVE' AND s.latest_version_id IS NOT NULL))";

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Skill> findOwnedReadableSkills(String ownerId,
            org.springframework.data.domain.Pageable pageable) {
        String from = " FROM skill s JOIN namespace n ON n.id = s.namespace_id"
                + " WHERE s.owner_id = :ownerId AND s.hidden = FALSE AND " + OWNED_READABLE;
        var rows = entityManager.createNativeQuery("SELECT s.*" + from + " ORDER BY s.updated_at DESC, s.id DESC",
                Skill.class);
        var count = entityManager.createNativeQuery("SELECT COUNT(*)" + from);
        rows.setParameter("ownerId", ownerId);
        count.setParameter("ownerId", ownerId);
        @SuppressWarnings("unchecked")
        List<Skill> items = rows.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize())
                .getResultList();
        return new org.springframework.data.domain.PageImpl<>(items, pageable,
                ((Number) count.getSingleResult()).longValue());
    }

    @Override
    public Page<Skill> findOwnedNamespaceSkills(String userId, String namespace, String keyword, Pageable page) {
        var spaces = entityManager.createQuery("""
                SELECT n.id FROM Namespace n, NamespaceMember m
                WHERE m.namespaceId = n.id AND m.userId = :owner AND n.slug = :slug
                """, Long.class).setParameter("owner", userId).setParameter("slug", namespace).getResultList();
        if (spaces.isEmpty()) {
            throw new DomainForbiddenException("error.namespace.membership.required");
        }
        String predicate = """
                FROM Skill s WHERE s.ownerId = :owner AND s.namespaceId = :space AND s.hidden = false
                  AND s.status NOT IN ('ARCHIVED')
                  AND (locate(:q, lower(s.slug)) > 0 OR locate(:q, lower(s.displayName)) > 0
                       OR locate(:q, lower(s.summary)) > 0 OR :q = '')
                """;
        long total = entityManager.createQuery("SELECT count(s) " + predicate, Long.class)
                .setParameter("owner", userId).setParameter("space", spaces.getFirst())
                .setParameter("q", keyword).getSingleResult();
        if (page.getOffset() >= total) return new PageImpl<>(List.of(), page, total);
        var skills = entityManager.createQuery("SELECT s " + predicate + " ORDER BY s.updatedAt DESC, s.id DESC", Skill.class)
                .setParameter("owner", userId).setParameter("space", spaces.getFirst()).setParameter("q", keyword)
                .setFirstResult(Math.toIntExact(page.getOffset())).setMaxResults(page.getPageSize()).getResultList();
        return new PageImpl<>(skills, page, total);
    }

    @Override
    public List<SkillSummaryResponse> getSkillSummaries(List<Skill> skills, String currentUserId) {
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<Long, Namespace> namespacesById = namespaceRepository.findByIdIn(
                        skills.stream().map(Skill::getNamespaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<String, UserAccount> ownersById = userAccountRepository == null
                ? Map.of()
                : userAccountRepository.findByIdIn(skills.stream().map(Skill::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        return skills.stream()
                .map(skill -> toSummaryResponse(skill, currentUserId, namespacesById, ownersById))
                .toList();
    }

    /** Page and count share this read-model predicate, including the existing owner-preview recency rule. */
    private static final String PENDING_FROM_WHERE = """
            FROM skill s
            JOIN namespace n ON n.id = s.namespace_id
            LEFT JOIN LATERAL (
                SELECT published.id, published.created_at FROM skill_version published
                WHERE published.skill_id = s.id AND published.status = 'PUBLISHED'
                ORDER BY CASE WHEN published.id = s.latest_version_id THEN 0 ELSE 1 END,
                         published.published_at DESC NULLS FIRST, published.created_at DESC, published.id DESC
                LIMIT 1
            ) published ON TRUE
            LEFT JOIN LATERAL (
                SELECT work.status FROM skill_version work
                WHERE work.skill_id = s.id AND work.status NOT IN ('PUBLISHED', 'YANKED')
                  AND (published.id IS NULL OR (work.created_at, work.id) > (published.created_at, published.id))
                ORDER BY work.created_at DESC, work.id DESC LIMIT 1
            ) preview ON TRUE
            WHERE s.owner_id = :ownerId AND s.hidden = FALSE AND s.status <> 'ARCHIVED'
              AND """ + OWNER_RETAINS_ACCESS + """
              AND (CAST(:namespaceId AS BIGINT) IS NULL OR s.namespace_id = :namespaceId)
              AND (:keyword = '' OR POSITION(:keyword IN LOWER(COALESCE(s.display_name, ''))) > 0
                   OR POSITION(:keyword IN LOWER(s.slug)) > 0
                   OR POSITION(:keyword IN LOWER(COALESCE(s.summary, ''))) > 0)
              AND (preview.status = 'PENDING_REVIEW' OR EXISTS (
                  SELECT 1 FROM review_task r WHERE r.skill_id = s.id
                    AND r.requested_visibility IS NOT NULL AND r.status = 'PENDING'
              ))
            """;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Skill> findPendingSkills(String ownerId, String keyword,
            Long namespaceId, org.springframework.data.domain.Pageable pageable) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        // Native PostgreSQL LATERAL mirrors the owner-preview resolver without per-skill lookups.
        var rows = entityManager.createNativeQuery("SELECT s.* " + PENDING_FROM_WHERE
                + " ORDER BY s.updated_at DESC, s.id DESC", Skill.class);
        var count = entityManager.createNativeQuery("SELECT COUNT(*) " + PENDING_FROM_WHERE);
        for (var statement : List.of(rows, count)) {
            statement.setParameter("ownerId", ownerId).setParameter("keyword", query)
                    .setParameter("namespaceId", namespaceId);
        }
        @SuppressWarnings("unchecked")
        List<Skill> items = rows.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize()).getResultList();
        return new org.springframework.data.domain.PageImpl<>(items, pageable, ((Number) count.getSingleResult()).longValue());
    }

    /**
     * One page-bounded management read. Authorizing related coordinates by the same owner avoids
     * revealing a private source through a public/global card. No per-card visibility requests.
     */
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<Long, SkillSummaryResponse.ManagementState> getManagementStates(List<Skill> skills, String ownerId) {
        List<Long> ids = skills.stream().filter(skill -> ownerId.equals(skill.getOwnerId()))
                .map(Skill::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        // App read-model joins stay in this query repository; domain repositories remain unchanged.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT s.id, pending.requested_visibility, pending.id,
                       CASE WHEN s.source_skill_id IS NOT NULL THEN 'GLOBAL_COPY'
                            WHEN target_ns.id IS NOT NULL THEN 'TEAM_SOURCE' END,
                       CASE WHEN s.source_skill_id IS NOT NULL THEN source_ns.slug
                            WHEN target.visibility <> 'NAMESPACE_ONLY' OR EXISTS (
                                SELECT 1 FROM namespace_member m
                                WHERE m.namespace_id = target.namespace_id AND m.user_id = :ownerId
                            ) THEN target_ns.slug END,
                       CASE WHEN s.source_skill_id IS NOT NULL THEN source.slug ELSE target.slug END
                FROM skill s
                LEFT JOIN LATERAL (
                    SELECT r.id, r.requested_visibility FROM review_task r
                    WHERE r.skill_id = s.id AND r.requested_visibility IS NOT NULL AND r.status = 'PENDING'
                    ORDER BY r.id DESC LIMIT 1
                ) pending ON TRUE
                LEFT JOIN skill source ON source.id = s.source_skill_id AND source.owner_id = :ownerId
                    AND source.hidden = FALSE AND source.status = 'ACTIVE'
                    AND (source.visibility <> 'NAMESPACE_ONLY' OR EXISTS (
                        SELECT 1 FROM namespace_member m WHERE m.namespace_id = source.namespace_id AND m.user_id = :ownerId
                    ))
                LEFT JOIN namespace source_ns ON source_ns.id = source.namespace_id AND source_ns.status = 'ACTIVE'
                LEFT JOIN skill target ON target.source_skill_id = s.id AND target.owner_id = :ownerId
                    AND target.hidden = FALSE AND target.status = 'ACTIVE'
                LEFT JOIN namespace target_ns ON target_ns.id = target.namespace_id
                    AND target_ns.type = 'GLOBAL' AND target_ns.status = 'ACTIVE'
                WHERE s.id IN :ids AND s.owner_id = :ownerId
                """)
                .setParameter("ids", ids).setParameter("ownerId", ownerId).getResultList();
        Map<Long, SkillSummaryResponse.ManagementState> states = new java.util.HashMap<>();
        for (Object[] row : rows) {
            states.put(((Number) row[0]).longValue(), new SkillSummaryResponse.ManagementState(
                    (String) row[1], row[2] == null ? null : ((Number) row[2]).longValue(),
                    (String) row[3], (String) row[4], row[4] == null ? null : (String) row[5]));
        }
        return states;
    }

    @Override
    public List<SkillSummaryResponse> getHiddenSkillSummaries(List<Skill> skills) {
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<Long, Namespace> namespacesById = namespaceRepository.findByIdIn(
                        skills.stream().map(Skill::getNamespaceId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<String, UserAccount> ownersById = userAccountRepository == null
                ? Map.of()
                : userAccountRepository.findByIdIn(skills.stream().map(Skill::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<Long, SkillLifecycleProjectionService.Projection> projections =
                skillLifecycleProjectionService.projectPublishedSummaries(skills);

        return skills.stream()
                .map(skill -> toSummaryResponse(
                        skill,
                        namespacesById,
                        ownersById,
                        projections.get(skill.getId()),
                        false
                ))
                .toList();
    }

    private SkillSummaryResponse toSummaryResponse(Skill skill,
                                                   String currentUserId,
                                                   Map<Long, Namespace> namespacesById,
                                                   Map<String, UserAccount> ownersById) {
        Namespace namespace = namespacesById.get(skill.getNamespaceId());
        // Summaries carry no namespace roles, so only the owner, while still allowed, sees unpublished work.
        SkillLifecycleProjectionService.Projection projection = skillLifecycleProjectionService.projectForViewer(
                skill, skill.getOwnerId().equals(currentUserId) && ownerAccess.ownerRetainsAccess(skill));
        return toSummaryResponse(
                skill,
                namespacesById,
                ownersById,
                projection,
                canSubmitPromotion(skill, projection.publishedVersion(), namespace)
        );
    }

    private SkillSummaryResponse toSummaryResponse(Skill skill,
                                                   Map<Long, Namespace> namespacesById,
                                                   Map<String, UserAccount> ownersById,
                                                   SkillLifecycleProjectionService.Projection projection,
                                                   boolean canSubmitPromotion) {
        Namespace namespace = namespacesById.get(skill.getNamespaceId());
        SkillLifecycleProjectionService.VersionProjection headlineVersion = projection.headlineVersion();
        SkillLifecycleProjectionService.VersionProjection publishedVersion = projection.publishedVersion();
        SkillLifecycleProjectionService.VersionProjection ownerPreviewVersion = projection.ownerPreviewVersion();

        return new SkillSummaryResponse(
                skill.getId(),
                skill.getSlug(),
                skill.getDisplayName(),
                skill.getSummary(),
                skill.getVisibility().name(),
                skill.getStatus().name(),
                skill.getDownloadCount(),
                skill.getStarCount(),
                skill.getRatingAvg(),
                skill.getRatingCount(),
                namespace != null ? namespace.getSlug() : null,
                skill.getUpdatedAt(),
                skill.getOwnerId(),
                ownersById.get(skill.getOwnerId()) != null
                        ? ownersById.get(skill.getOwnerId()).getDisplayName()
                        : null,
                canSubmitPromotion,
                toLifecycleVersion(headlineVersion),
                toLifecycleVersion(publishedVersion),
                toLifecycleVersion(ownerPreviewVersion),
                projection.resolutionMode().name(),
                null,
                null,
                SkillAvatarUrls.current(skill, namespace != null ? namespace.getSlug() : "")
        );
    }

    private boolean canSubmitPromotion(Skill skill,
                                       SkillLifecycleProjectionService.VersionProjection publishedVersion,
                                       Namespace namespace) {
        if (namespace == null) {
            return false;
        }
        if (namespace.getType() != NamespaceType.TEAM) {
            return false;
        }
        if (namespace.getStatus() != NamespaceStatus.ACTIVE || skill.getStatus() != SkillStatus.ACTIVE) {
            return false;
        }
        if (promotionRequestRepository.findBySourceSkillIdAndStatus(skill.getId(), ReviewTaskStatus.PENDING).isPresent()) {
            return false;
        }
        return publishedVersion != null && "PUBLISHED".equals(publishedVersion.status());
    }

    private SkillLifecycleVersionResponse toLifecycleVersion(SkillLifecycleProjectionService.VersionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new SkillLifecycleVersionResponse(projection.id(), projection.version(), projection.status(),
                projection.displayName(), projection.summary(), projection.syncRevision());
    }
}
