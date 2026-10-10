package com.iflytek.skillhub.service.bundle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.config.SkillSuiteBundleProperties;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.suite.bundle.SkillSuiteBundlePreviewSession;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Creates actor-bound PreviewSessions after file and semantic checks complete without side effects. */
@Service
public class SkillSuiteBundlePreviewAppService {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final SkillSuiteBundleArchiveService archiveService;
    private final SkillSuiteBundlePreviewPlanner planner;
    private final SkillSuiteBundlePreviewPersistenceService persistenceService;
    private final SkillSuiteBundleProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SkillSuiteBundlePreviewAppService(
            SkillSuiteBundleArchiveService archiveService,
            SkillSuiteBundlePreviewPlanner planner,
            SkillSuiteBundlePreviewPersistenceService persistenceService,
            SkillSuiteBundleProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.archiveService = archiveService;
        this.planner = planner;
        this.persistenceService = persistenceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public PreviewOutcome preview(
            MultipartFile upload,
            String actorId,
            Map<Long, NamespaceRole> namespaceRoles,
            Set<String> platformRoles
    ) throws IOException {
        SkillSuiteBundleArchiveService.StagedBundleAnalysis staged = archiveService.stageAndAnalyze(upload);
        if (!staged.analysis().confirmable()) {
            return new PreviewOutcome(null, null, staged.analysis(), null);
        }

        SkillSuiteBundlePreviewPlanner.PreviewPlan plan = planner.plan(
                staged.analysis(), actorId, namespaceRoles, platformRoles);
        if (!plan.confirmable()) {
            archiveService.cleanupStagedObjects(staged.objectKeys());
            return new PreviewOutcome(null, null, staged.analysis(), plan);
        }

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getPreviewTtl());
        String token = UUID.randomUUID().toString();
        try {
            Map<String, Object> manifestJson = objectMapper.convertValue(
                    staged.analysis().manifest(), JSON_OBJECT);
            Map<String, Object> planJson = objectMapper.convertValue(plan, JSON_OBJECT);
            SkillSuiteBundlePreviewSession session = new SkillSuiteBundlePreviewSession(
                    token, actorId, plan.mode(), plan.targetNamespaceId(), plan.target().slug(),
                    plan.targetSuiteId(), plan.baseSuiteVersionId(), plan.targetVersion(),
                    staged.archiveObjectKey(), staged.archiveSha256(), manifestJson, planJson,
                    plan.warningDigest(), expiresAt, now);
            persistenceService.save(session);
            return new PreviewOutcome(token, expiresAt, staged.analysis(), plan);
        } catch (RuntimeException exception) {
            archiveService.cleanupStagedObjects(staged.objectKeys());
            throw exception;
        }
    }

    public record PreviewOutcome(
            String previewToken,
            Instant expiresAt,
            SkillSuiteBundlePackageAnalyzer.BundleAnalysis packageAnalysis,
            SkillSuiteBundlePreviewPlanner.PreviewPlan plan
    ) {
        public boolean confirmable() {
            return previewToken != null;
        }

        public List<String> errors() {
            if (plan != null) {
                return plan.errors();
            }
            return java.util.stream.Stream.concat(
                            packageAnalysis.errors().stream(),
                            packageAnalysis.packageMembers().stream().flatMap(member ->
                                    member.validation().errors().stream()
                                            .map(error -> member.coordinate().canonical() + ": " + error)))
                    .toList();
        }
    }
}
