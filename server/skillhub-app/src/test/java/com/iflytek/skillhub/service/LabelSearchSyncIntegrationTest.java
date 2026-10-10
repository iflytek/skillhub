package com.iflytek.skillhub.service;

import com.iflytek.skillhub.SkillhubApplication;
import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionRepository;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.label.LabelTranslationRepository;
import com.iflytek.skillhub.domain.label.LabelType;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.dto.AdminLabelUpdateRequest;
import com.iflytek.skillhub.dto.LabelTranslationItemRequest;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentEntity;
import com.iflytek.skillhub.infra.jpa.SkillSearchDocumentJpaRepository;
import com.iflytek.skillhub.search.SearchEmbeddingService;
import com.iflytek.skillhub.search.SearchRebuildService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Reproduces the bug where attaching a skill label does not update the search
 * index. The label keyword should appear in the rebuilt search document after
 * {@code attachLabel} commits.
 *
 * <p>With the upstream (synchronous) {@code LabelSearchSyncService.rebuildSkill},
 * the rebuild runs inside the {@code afterCommit} callback on the request thread,
 * where the {@code @Transactional index()} write does not persist — so the keyword
 * never lands in the index and this test fails. Adding {@code @Async} moves the
 * rebuild to a fresh thread/transaction and the keyword appears.
 */
@SpringBootTest(classes = SkillhubApplication.class)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class LabelSearchSyncIntegrationTest {

    @Autowired
    private SkillLabelAppService skillLabelAppService;

    @Autowired
    private LabelAdminAppService labelAdminAppService;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private LabelDefinitionRepository labelDefinitionRepository;

    @Autowired
    private LabelTranslationRepository labelTranslationRepository;

    @Autowired
    private SkillSearchDocumentJpaRepository skillSearchDocumentJpaRepository;

    @Autowired
    private SearchRebuildService searchRebuildService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private SearchEmbeddingService searchEmbeddingService;

    @MockBean
    private RbacService rbacService;

    @BeforeEach
    void setUp() {
        when(searchEmbeddingService.embed(anyString())).thenReturn("");
        when(searchEmbeddingService.similarity(anyString(), anyString())).thenReturn(0.0d);
        when(rbacService.getUserRoleCodes(anyString())).thenReturn(Set.of("SUPER_ADMIN"));
    }

    @Test
    void attachingLabel_updatesSearchIndexWithLabelKeyword() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerId = "owner-" + suffix;
        // ASCII display name so the tokenizer keeps it as a single searchable token.
        String labelDisplayName = "MachineLearning" + suffix;
        String labelSlug = "ml-" + suffix;

        Namespace namespace = new Namespace("ns-" + suffix, "NS " + suffix, ownerId);
        namespace.setType(NamespaceType.GLOBAL);
        namespace = namespaceRepository.save(namespace);

        Skill skill = new Skill(namespace.getId(), "skill-" + suffix, ownerId, SkillVisibility.PUBLIC);
        skill.setDisplayName("Skill " + suffix);
        skill.setSummary("A skill used to reproduce the label search sync bug.");
        skill.setCreatedBy(ownerId);
        skill.setUpdatedBy(ownerId);
        skill = skillRepository.save(skill);
        skillRepository.flush();

        LabelDefinition label = labelDefinitionRepository.save(
                new LabelDefinition(labelSlug, LabelType.RECOMMENDED, true, 0, ownerId));
        labelTranslationRepository.saveAll(List.of(
                new LabelTranslation(label.getId(), "en", labelDisplayName)));
        labelTranslationRepository.flush();

        // Baseline: nothing indexed yet.
        assertThat(skillSearchDocumentJpaRepository.findBySkillId(skill.getId())).isEmpty();

        // Act: attach the label as the skill owner (passes resolve + permission checks).
        Map<Long, NamespaceRole> ownerRoles = Map.of(namespace.getId(), NamespaceRole.OWNER);
        skillLabelAppService.attachLabel(
                namespace.getSlug(),
                skill.getSlug(),
                labelSlug,
                ownerId,
                ownerRoles,
                new AuditRequestContext("127.0.0.1", "junit"));

        // Assert: the rebuilt search document must contain the label keyword.
        SkillSearchDocumentEntity indexed = awaitIndexedDocument(skill.getId());
        assertThat(indexed.getKeywords())
                .as("label keyword should be indexed after attachLabel commits")
                .contains(labelDisplayName);
    }

    @Test
    void detachingLabel_removesKeywordFromSearchIndex() throws Exception {
        Fixture f = createFixture();

        skillLabelAppService.attachLabel(
                f.namespaceSlug, f.skillSlug, f.labelSlug, f.ownerId, f.ownerRoles, auditContext());
        SkillSearchDocumentEntity afterAttach = awaitIndexedDocument(f.skillId);
        assertThat(afterAttach.getKeywords())
                .as("precondition: label keyword indexed after attach")
                .contains(f.labelDisplayName);

        // Act: detach the same label.
        skillLabelAppService.detachLabel(
                f.namespaceSlug, f.skillSlug, f.labelSlug, f.ownerId, f.ownerRoles, auditContext());

        // Assert: the rebuilt document must no longer contain the label keyword.
        awaitKeywordAbsent(f.skillId, f.labelDisplayName);
    }

    /**
     * Guards against the {@code CallerRunsPolicy} regression: when the executor is
     * saturated, {@code rebuildSkill} runs synchronously on the request thread inside
     * the {@code afterCommit} phase — the exact context where the index write used to be
     * dropped. This exercises that path directly (no async hop) and asserts the document
     * is still persisted, proving the fix relies on {@code REQUIRES_NEW}, not on the
     * executor having spare capacity.
     */
    @Test
    void syncRebuildInAfterCommitPhase_persistsIndex() throws Exception {
        Fixture f = createFixture();

        // Establish the skill-label association and a baseline index via the normal path.
        skillLabelAppService.attachLabel(
                f.namespaceSlug, f.skillSlug, f.labelSlug, f.ownerId, f.ownerRoles, auditContext());
        awaitIndexedDocument(f.skillId);

        // Clear the index so we can observe the synchronous rebuild in isolation.
        transactionTemplate.executeWithoutResult(
                status -> skillSearchDocumentJpaRepository.deleteBySkillId(f.skillId));
        assertThat(skillSearchDocumentJpaRepository.findBySkillId(f.skillId)).isEmpty();

        // Rebuild synchronously on the caller thread, inside a post-commit synchronization
        // (mirrors the CallerRuns fallback from afterCommit(() -> rebuildSkill(...))).
        transactionTemplate.executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        searchRebuildService.rebuildBySkill(f.skillId);
                    }
                }));

        SkillSearchDocumentEntity indexed = skillSearchDocumentJpaRepository.findBySkillId(f.skillId)
                .orElseThrow(() -> new AssertionError(
                        "synchronous rebuild in afterCommit phase must persist the index document"));
        assertThat(indexed.getKeywords())
                .as("label keyword must be indexed even on the synchronous caller-runs path")
                .contains(f.labelDisplayName);
    }

    @Test
    void updatingLabelTranslations_rebuildsAffectedSkillSearchKeywords() throws Exception {
        Fixture f = createFixture();
        String updatedEnglish = "DeepLearning" + UUID.randomUUID().toString().substring(0, 8);
        String updatedChinese = "深度学习" + UUID.randomUUID().toString().substring(0, 8);

        skillLabelAppService.attachLabel(
                f.namespaceSlug, f.skillSlug, f.labelSlug, f.ownerId, f.ownerRoles, auditContext());
        SkillSearchDocumentEntity afterAttach = awaitIndexedDocument(f.skillId);
        assertThat(afterAttach.getKeywords()).contains(f.labelDisplayName);

        labelAdminAppService.update(
                f.labelSlug,
                new AdminLabelUpdateRequest(
                        LabelType.RECOMMENDED,
                        true,
                        0,
                        List.of(
                                new LabelTranslationItemRequest("en", updatedEnglish),
                                new LabelTranslationItemRequest("zh-CN", updatedChinese)
                        )
                ),
                f.ownerId,
                auditContext()
        );

        awaitKeywordPresent(f.skillId, updatedEnglish);
        SkillSearchDocumentEntity afterUpdate = skillSearchDocumentJpaRepository.findBySkillId(f.skillId)
                .orElseThrow();
        assertThat(afterUpdate.getKeywords())
                .contains(updatedEnglish)
                .contains(updatedChinese)
                .doesNotContain(f.labelDisplayName);
    }

    private SkillSearchDocumentEntity awaitIndexedDocument(Long skillId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        Optional<SkillSearchDocumentEntity> indexed = skillSearchDocumentJpaRepository.findBySkillId(skillId);
        while (indexed.isEmpty() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100L);
            indexed = skillSearchDocumentJpaRepository.findBySkillId(skillId);
        }
        return indexed.orElseThrow(
                () -> new AssertionError("Expected search document for skill " + skillId));
    }

    private void awaitKeywordAbsent(Long skillId, String keyword) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            Optional<SkillSearchDocumentEntity> indexed =
                    skillSearchDocumentJpaRepository.findBySkillId(skillId);
            if (indexed.isPresent() && !indexed.get().getKeywords().contains(keyword)) {
                return;
            }
            Thread.sleep(100L);
        }
        String keywords = skillSearchDocumentJpaRepository.findBySkillId(skillId)
                .map(SkillSearchDocumentEntity::getKeywords)
                .orElse("<no document>");
        throw new AssertionError(
                "Expected keyword '" + keyword + "' to be removed from index for skill "
                        + skillId + " but keywords were: " + keywords);
    }

    private void awaitKeywordPresent(Long skillId, String keyword) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            Optional<SkillSearchDocumentEntity> indexed =
                    skillSearchDocumentJpaRepository.findBySkillId(skillId);
            if (indexed.isPresent() && indexed.get().getKeywords().contains(keyword)) {
                return;
            }
            Thread.sleep(100L);
        }
        String keywords = skillSearchDocumentJpaRepository.findBySkillId(skillId)
                .map(SkillSearchDocumentEntity::getKeywords)
                .orElse("<no document>");
        throw new AssertionError(
                "Expected keyword '" + keyword + "' to be present in index for skill "
                        + skillId + " but keywords were: " + keywords);
    }

    private AuditRequestContext auditContext() {
        return new AuditRequestContext("127.0.0.1", "junit");
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerId = "owner-" + suffix;
        // ASCII display name so the tokenizer keeps it as a single searchable token.
        String labelDisplayName = "MachineLearning" + suffix;
        String labelSlug = "ml-" + suffix;

        Namespace namespace = new Namespace("ns-" + suffix, "NS " + suffix, ownerId);
        namespace.setType(NamespaceType.GLOBAL);
        namespace = namespaceRepository.save(namespace);

        Skill skill = new Skill(namespace.getId(), "skill-" + suffix, ownerId, SkillVisibility.PUBLIC);
        skill.setDisplayName("Skill " + suffix);
        skill.setSummary("A skill used to reproduce the label search sync bug.");
        skill.setCreatedBy(ownerId);
        skill.setUpdatedBy(ownerId);
        skill = skillRepository.save(skill);
        skillRepository.flush();

        LabelDefinition label = labelDefinitionRepository.save(
                new LabelDefinition(labelSlug, LabelType.RECOMMENDED, true, 0, ownerId));
        labelTranslationRepository.saveAll(List.of(
                new LabelTranslation(label.getId(), "en", labelDisplayName)));
        labelTranslationRepository.flush();

        return new Fixture(
                namespace.getSlug(), skill.getSlug(), skill.getId(),
                labelSlug, labelDisplayName, ownerId,
                Map.of(namespace.getId(), NamespaceRole.OWNER));
    }

    private record Fixture(
            String namespaceSlug,
            String skillSlug,
            Long skillId,
            String labelSlug,
            String labelDisplayName,
            String ownerId,
            Map<Long, NamespaceRole> ownerRoles) {
    }
}
