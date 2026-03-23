package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityScanRequest;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;

public class ScanTaskConsumer extends AbstractStreamConsumer<ScanTaskConsumer.ScanTaskPayload> {
    private static final Path SCAN_TEMP_DIR = Paths.get("/tmp/skillhub-scans").toAbsolutePath().normalize();

    private final SecurityScanner securityScanner;
    private final SecurityScanService securityScanService;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillRepository skillRepository;
    private final ReviewTaskRepository reviewTaskRepository;
    private final ScanTaskProducer scanTaskProducer;

    public ScanTaskConsumer(RedisConnectionFactory connectionFactory,
                            String streamKey,
                            String groupName,
                            SecurityScanner securityScanner,
                            SecurityScanService securityScanService,
                            SkillVersionRepository skillVersionRepository,
                            SkillRepository skillRepository,
                            ReviewTaskRepository reviewTaskRepository,
                            ScanTaskProducer scanTaskProducer) {
        super(connectionFactory, streamKey, groupName);
        this.securityScanner = securityScanner;
        this.securityScanService = securityScanService;
        this.skillVersionRepository = skillVersionRepository;
        this.skillRepository = skillRepository;
        this.reviewTaskRepository = reviewTaskRepository;
        this.scanTaskProducer = scanTaskProducer;
    }

    @Override
    protected String taskDisplayName() {
        return "Security Scan";
    }

    @Override
    protected String consumerPrefix() {
        return "scanner";
    }

    @Override
    protected ScanTaskPayload parsePayload(String messageId, Map<String, String> data) {
        String versionId = data.get("versionId");
        if (versionId == null || versionId.isEmpty()) {
            return null;
        }
        try {
            String scannerTypeValue = data.getOrDefault("scannerType", ScannerType.SKILL_SCANNER.getValue());
            ScannerType scannerType = ScannerType.fromValue(scannerTypeValue);
            return new ScanTaskPayload(
                    data.get("taskId"),
                    Long.valueOf(versionId),
                    data.get("skillPath"),
                    scannerType
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(ScanTaskPayload payload) {
        return "taskId=" + payload.taskId + ", versionId=" + payload.versionId + ", scanner=" + payload.scannerType;
    }

    @Override
    protected void markProcessing(ScanTaskPayload payload) {
    }

    @Override
    protected void processBusiness(ScanTaskPayload payload) {
        SecurityScanRequest request = new SecurityScanRequest(
                payload.taskId,
                payload.versionId,
                payload.skillPath,
                Map.of()
        );
        SecurityScanResponse response = securityScanner.scan(request);
        securityScanService.processScanResult(payload.versionId, payload.scannerType, response);
    }

    @Override
    protected void markCompleted(ScanTaskPayload payload) {
        cleanupTempPath(payload.skillPath);
    }

    @Override
    protected void markFailed(ScanTaskPayload payload, String error) {
        try {
            skillVersionRepository.findById(payload.versionId)
                    .filter(version -> version.getStatus() == SkillVersionStatus.SCANNING)
                    .ifPresent(version -> {
                        version.setStatus(SkillVersionStatus.SCAN_FAILED);
                        skillVersionRepository.save(version);
                        skillRepository.findById(version.getSkillId())
                                .ifPresent(skill -> reviewTaskRepository.save(
                                        new ReviewTask(payload.versionId, skill.getNamespaceId(), version.getCreatedBy())
                                ));
                    });
        } finally {
            cleanupTempPath(payload.skillPath);
        }
    }

    @Override
    protected void retryMessage(ScanTaskPayload payload, int retryCount) {
        scanTaskProducer.publishScanTask(new ScanTask(
                payload.taskId,
                payload.versionId,
                payload.skillPath,
                null,
                System.currentTimeMillis(),
                Map.of("retryCount", String.valueOf(retryCount))
        ));
    }

    private void cleanupTempPath(String skillPath) {
        try {
            Path path = Paths.get(skillPath).toAbsolutePath().normalize();
            if (!path.startsWith(SCAN_TEMP_DIR)) {
                log.warn("Skipping cleanup for path outside scan temp directory: {}", skillPath);
                return;
            }
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            } else if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup temp path: {}", skillPath, e);
        }
    }

    protected record ScanTaskPayload(
            String taskId,
            Long versionId,
            String skillPath,
            ScannerType scannerType
    ) {
    }
}
