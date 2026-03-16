package com.iflytek.skillhub.domain.security;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SecurityScanService {

    private static final Logger log = LoggerFactory.getLogger(SecurityScanService.class);
    private static final String TEMP_DIR = "/tmp/skillhub-scans";

    private final SecurityAuditRepository auditRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final ScanTaskProducer scanTaskProducer;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String scanMode;
    private final boolean enabled;

    public SecurityScanService(SecurityAuditRepository auditRepository,
                               SkillVersionRepository skillVersionRepository,
                               ScanTaskProducer scanTaskProducer,
                               ApplicationEventPublisher eventPublisher,
                               ObjectMapper objectMapper,
                               @org.springframework.beans.factory.annotation.Value("${skillhub.security.scanner.mode:local}") String scanMode,
                               @org.springframework.beans.factory.annotation.Value("${skillhub.security.scanner.enabled:false}") boolean enabled) {
        this.auditRepository = auditRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.scanTaskProducer = scanTaskProducer;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.scanMode = scanMode;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional
    public void triggerScan(Long versionId, List<PackageEntry> entries, String publisherId) {
        if (!enabled) {
            log.debug("Security scanner is disabled, skipping scan for versionId={}", versionId);
            return;
        }

        String packagePath;
        if ("local".equalsIgnoreCase(scanMode)) {
            packagePath = saveTempDirectory(versionId, entries).toString();
        } else {
            packagePath = saveTempZip(versionId, entries).toString();
        }

        SecurityAudit audit = new SecurityAudit(versionId, "skill-scanner");
        auditRepository.save(audit);

        ScanTask task = new ScanTask(
                UUID.randomUUID().toString(),
                versionId,
                packagePath,
                publisherId,
                System.currentTimeMillis(),
                Map.of()
        );
        scanTaskProducer.publishScanTask(task);

        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("SkillVersion not found: " + versionId));
        version.setStatus(SkillVersionStatus.SCANNING);
        skillVersionRepository.save(version);

        log.info("Triggered security scan: versionId={}, taskId={}", versionId, task.taskId());
    }

    @Transactional
    public void processScanResult(Long versionId, SecurityScanResponse response) {
        SecurityAudit audit = auditRepository.findBySkillVersionId(versionId)
                .orElseThrow(() -> new IllegalStateException("SecurityAudit not found for versionId: " + versionId));

        audit.setScanId(response.scanId());
        audit.setVerdict(response.verdict());
        audit.setIsSafe(response.verdict() == SecurityVerdict.SAFE);
        audit.setMaxSeverity(response.maxSeverity());
        audit.setFindingsCount(response.findingsCount());
        audit.setFindings(serializeFindings(response.findings()));
        audit.setScanDurationSeconds(response.scanDurationSeconds());
        audit.setScannedAt(LocalDateTime.now());
        auditRepository.save(audit);

        SkillVersion version = skillVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("SkillVersion not found: " + versionId));
        version.setStatus(mapVerdictToStatus(response.verdict()));
        skillVersionRepository.save(version);

        eventPublisher.publishEvent(new ScanCompletedEvent(
                versionId, response.verdict(), response.findingsCount()));

        log.info("Processed scan result: versionId={}, verdict={}, findings={}",
                versionId, response.verdict(), response.findingsCount());
    }

    private SkillVersionStatus mapVerdictToStatus(SecurityVerdict verdict) {
        return SkillVersionStatus.PENDING_REVIEW;
    }

    private Path saveTempZip(Long versionId, List<PackageEntry> entries) {
        try {
            Path dir = Paths.get(TEMP_DIR);
            Files.createDirectories(dir);
            Path zipPath = dir.resolve(versionId + ".zip");

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (PackageEntry entry : entries) {
                    zos.putNextEntry(new ZipEntry(entry.path()));
                    zos.write(entry.content());
                    zos.closeEntry();
                }
                zos.finish();
                Files.write(zipPath, baos.toByteArray());
            }

            return zipPath;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save temp ZIP for versionId: " + versionId, e);
        }
    }

    private Path saveTempDirectory(Long versionId, List<PackageEntry> entries) {
        try {
            Path skillDir = Paths.get(TEMP_DIR, String.valueOf(versionId));
            Files.createDirectories(skillDir);

            for (PackageEntry entry : entries) {
                Path filePath = skillDir.resolve(entry.path());
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, entry.content());
            }

            return skillDir;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save temp directory for versionId: " + versionId, e);
        }
    }

    private String serializeFindings(List<SecurityFinding> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize findings, using empty array", e);
            return "[]";
        }
    }
}
