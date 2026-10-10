package com.iflytek.skillhub.domain.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SecurityScanService {

    private static final Logger log = LoggerFactory.getLogger(SecurityScanService.class);
    private static final String TEMP_DIR = "/tmp/skillhub-scans";
    private static final Path TEMP_BASE_DIR = Paths.get(TEMP_DIR).toAbsolutePath().normalize();

    private final SecurityAuditRepository auditRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final ScanTaskOutboxRepository scanTaskOutboxRepository;
    private final ScanTaskProducer scanTaskProducer;
    private final ObjectMapper objectMapper;
    private final String scanMode;
    private final boolean enabled;

    @Autowired
    public SecurityScanService(SecurityAuditRepository auditRepository,
                               SkillVersionRepository skillVersionRepository,
                               ScanTaskProducer scanTaskProducer,
                               ObjectMapper objectMapper,
                               @Value("${skillhub.security.scanner.mode:local}") String scanMode,
                               @Value("${skillhub.security.scanner.enabled:false}") boolean enabled,
                               ScanTaskOutboxRepository scanTaskOutboxRepository) {
        this.auditRepository = auditRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.scanTaskProducer = scanTaskProducer;
        this.objectMapper = objectMapper;
        this.scanMode = scanMode;
        this.enabled = enabled;
        this.scanTaskOutboxRepository = scanTaskOutboxRepository;
    }

    public SecurityScanService(SecurityAuditRepository auditRepository,
                               SkillVersionRepository skillVersionRepository,
                               ScanTaskProducer scanTaskProducer,
                               ObjectMapper objectMapper,
                               String scanMode,
                               boolean enabled) {
        this(auditRepository, skillVersionRepository, scanTaskProducer, objectMapper, scanMode, enabled, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public ScanTask retryStoredBundleScan(SkillVersion version, String bundleKey, String publisherId) {
        if (!enabled) {
            throw new IllegalStateException("Security scanner is disabled");
        }
        if (version.getStatus() != SkillVersionStatus.SCAN_FAILED) {
            throw new IllegalStateException("Only SCAN_FAILED versions can be retried");
        }
        ScanTask scanTask = new ScanTask(
                UUID.randomUUID().toString(),
                version.getId(),
                null,
                bundleKey,
                publisherId,
                System.currentTimeMillis(),
                Map.of("scannerType", ScannerType.SKILL_SCANNER.getValue())
        );
        persistScanAttempt(version, scanTask);
        return scanTask;
    }

    @Transactional
    public void triggerScan(Long versionId, List<PackageEntry> entries, String publisherId) {
        if (!enabled) {
            log.debug("Security scanner disabled, skipping trigger for versionId={}", versionId);
            return;
        }

        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("SkillVersion not found: " + versionId));

        String packagePath = null;
        String bundleKey = null;
        if ("upload".equalsIgnoreCase(scanMode)) {
            validateUploadEntries(entries);
            bundleKey = buildBundleStorageKey(version.getSkillId(), versionId);
        } else {
            packagePath = saveTempDirectory(versionId, entries).toString();
        }
        final ScanTask scanTask = new ScanTask(
                UUID.randomUUID().toString(),
                versionId,
                packagePath,
                bundleKey,
                publisherId,
                System.currentTimeMillis(),
                Map.of("scannerType", ScannerType.SKILL_SCANNER.getValue())
        );
        persistScanAttempt(version, scanTask);
    }

    private void persistScanAttempt(SkillVersion version, ScanTask scanTask) {
        // A new record preserves prior scan history while identifying this attempt independently.
        auditRepository.save(new SecurityAudit(version.getId(), ScannerType.SKILL_SCANNER, scanTask.taskId()));
        if (scanTaskOutboxRepository != null) {
            scanTaskOutboxRepository.save(new ScanTaskOutbox(scanTask));
        } else {
            TransactionCommitCallbacks.afterCommitOrNow(() -> scanTaskProducer.publishScanTask(scanTask));
        }
        // Only transition to SCANNING if the version is not already published (auto-publish flow)
        if (version.getStatus() != SkillVersionStatus.PUBLISHED) {
            version.setStatus(SkillVersionStatus.SCANNING);
            skillVersionRepository.save(version);
        }
    }

    public boolean isTaskAlreadyProcessed(String taskId) {
        return taskId != null && auditRepository != null
                && auditRepository.existsByTaskIdAndScannedAtIsNotNull(taskId);
    }

    @Transactional
    public void processScanFailure(String taskId, Long versionId, ScannerType scannerType, String reason) {
        SecurityAudit audit = auditRepository.findByTaskId(taskId)
                .filter(candidate -> candidate.getSkillVersionId().equals(versionId))
                .filter(candidate -> candidate.getScannerType() == scannerType)
                .orElseThrow(() -> new IllegalStateException("SecurityAudit not found for taskId=" + taskId));
        if (audit.getScannedAt() != null) {
            return;
        }
        audit.markFailed(Instant.now(Clock.systemUTC()), reason);
        auditRepository.save(audit);

        boolean currentAttempt = auditRepository
                .findLatestActiveByVersionIdAndScannerType(versionId, scannerType)
                .map(latest -> taskId.equals(latest.getTaskId()))
                .orElse(false);
        if (!currentAttempt) {
            return;
        }
        skillVersionRepository.findById(versionId)
                .filter(version -> version.getStatus() == SkillVersionStatus.SCANNING)
                .ifPresent(version -> {
                    version.setStatus(SkillVersionStatus.SCAN_FAILED);
                    skillVersionRepository.save(version);
                });
    }

    @Transactional
    public void processScanResult(String taskId,
                                  Long versionId,
                                  ScannerType scannerType,
                                  SecurityScanResponse response) {
        SecurityAudit audit = auditRepository.findByTaskId(taskId)
                .filter(candidate -> candidate.getSkillVersionId().equals(versionId))
                .filter(candidate -> candidate.getScannerType() == scannerType)
                .orElseThrow(() -> new IllegalStateException(
                        "SecurityAudit not found for taskId=" + taskId));
        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("SkillVersion not found: " + versionId));

        audit.setScanId(response.scanId());
        audit.setVerdict(response.verdict());
        audit.setIsSafe(response.verdict() == SecurityVerdict.SAFE);
        audit.setMaxSeverity(response.maxSeverity());
        audit.setFindingsCount(response.findingsCount());
        audit.setFindings(serializeFindings(response.findings()));
        audit.setScanDurationSeconds(response.scanDurationSeconds());
        audit.setScannedAt(Instant.now(Clock.systemUTC()));
        auditRepository.save(audit);

        boolean currentAttempt = auditRepository
                .findLatestActiveByVersionIdAndScannerType(versionId, scannerType)
                .map(latest -> taskId.equals(latest.getTaskId()))
                .orElse(false);
        // A late result is retained on its own audit round but cannot complete a newer attempt.
        if (currentAttempt && version.getStatus() == SkillVersionStatus.SCANNING) {
            if (version.getRequestedVisibility() == SkillVisibility.PRIVATE) {
                version.setStatus(SkillVersionStatus.UPLOADED);
            } else {
                version.setStatus(SkillVersionStatus.PENDING_REVIEW);
            }
        }
        if (currentAttempt) {
            skillVersionRepository.save(version);
        }
    }

    private Path saveTempDirectory(Long versionId, List<PackageEntry> entries) {
        try {
            Path skillDir = TEMP_BASE_DIR.resolve(String.valueOf(versionId)).normalize();
            Files.createDirectories(skillDir);
            for (PackageEntry entry : entries) {
                Path filePath = resolveSafeChild(skillDir, entry.path());
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream input = entry.openStream()) {
                    Files.copy(input, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return skillDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save temp directory for versionId: " + versionId, e);
        }
    }

    private void validateUploadEntries(List<PackageEntry> entries) {
        for (PackageEntry entry : entries) {
            safeZipEntryName(entry.path());
        }
    }

    private String buildBundleStorageKey(Long skillId, Long versionId) {
        return String.format("packages/%d/%d/bundle.zip", skillId, versionId);
    }

    private String serializeFindings(List<SecurityFinding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize findings for security audit", e);
            return "[]";
        }
    }

    private Path resolveSafeChild(Path baseDir, String entryPath) {
        Path resolved = baseDir.resolve(entryPath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalStateException("Unsafe scan path: " + entryPath);
        }
        return resolved;
    }

    private String safeZipEntryName(String entryPath) {
        Path normalized = Paths.get(entryPath).normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new IllegalStateException("Unsafe scan path: " + entryPath);
        }
        String safePath = normalized.toString().replace('\\', '/');
        if (safePath.isBlank() || safePath.startsWith("../")) {
            throw new IllegalStateException("Unsafe scan path: " + entryPath);
        }
        return safePath;
    }

    /**
     * Soft delete all audit records for a given skill version.
     * Called before physically deleting a skill version to preserve audit history.
     */
    @Transactional
    public void softDeleteByVersionId(Long versionId) {
        if (scanTaskOutboxRepository != null) {
            scanTaskOutboxRepository.deleteByVersionId(versionId);
        }
        List<SecurityAudit> audits = auditRepository.findAllActiveBySkillVersionId(versionId);
        if (audits.isEmpty()) {
            log.debug("No active security audits to soft-delete for versionId={}", versionId);
            return;
        }
        audits.forEach(SecurityAudit::markAsDeleted);
        auditRepository.saveAll(audits);
        log.info("Soft deleted {} security audit(s) for versionId={}", audits.size(), versionId);
    }

    /**
     * Physically delete all audit records for a given skill version.
     * Called during hard delete when the entire skill is being permanently removed.
     */
    @Transactional
    public void hardDeleteByVersionId(Long versionId) {
        auditRepository.deleteBySkillVersionId(versionId);
        if (scanTaskOutboxRepository != null) {
            scanTaskOutboxRepository.deleteByVersionId(versionId);
        }
    }
}
