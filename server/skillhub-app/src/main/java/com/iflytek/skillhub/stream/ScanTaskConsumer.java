package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.*;
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

/**
 * Security scan task consumer
 */
public class ScanTaskConsumer extends AbstractStreamConsumer<ScanTaskConsumer.ScanTaskPayload> {

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
        log.info("ScanTaskConsumer bean created: streamKey={}, groupName={}", streamKey, groupName);
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
        String versionIdStr = data.get("versionId");
        if (versionIdStr == null || versionIdStr.isEmpty()) {
            return null;
        }

        try {
            return new ScanTaskPayload(
                    data.get("taskId"),
                    Long.valueOf(versionIdStr),
                    data.get("skillPath")
            );
        } catch (NumberFormatException e) {
            log.warn("Failed to parse message: messageId={}, data={}", messageId, data);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(ScanTaskPayload payload) {
        return "taskId=" + payload.taskId + ", versionId=" + payload.versionId;
    }

    @Override
    protected void markProcessing(ScanTaskPayload payload) {
        // Status already set to SCANNING in triggerScan
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
        securityScanService.processScanResult(payload.versionId, response);

        log.info("Scan completed: taskId={}, verdict={}", payload.taskId, response.verdict());
    }

    @Override
    protected void markCompleted(ScanTaskPayload payload) {
        cleanupTempPath(payload.skillPath);
    }

    @Override
    protected void markFailed(ScanTaskPayload payload, String error) {
        try {
            skillVersionRepository.findById(payload.versionId)
                    .filter(v -> v.getStatus() == SkillVersionStatus.SCANNING)
                    .ifPresent(version -> {
                        version.setStatus(SkillVersionStatus.SCAN_FAILED);
                        skillVersionRepository.save(version);

                        skillRepository.findById(version.getSkillId())
                                .ifPresent(skill -> {
                                    var reviewTask = new ReviewTask(
                                            payload.versionId,
                                            skill.getNamespaceId(),
                                            version.getCreatedBy()
                                    );
                                    reviewTaskRepository.save(reviewTask);
                                    log.info("Created review task: versionId={}", payload.versionId);
                                });
                    });
        } catch (Exception e) {
            log.error("Failed to mark scan failure: versionId={}", payload.versionId, e);
        } finally {
            cleanupTempPath(payload.skillPath);
        }
    }

    @Override
    protected void retryMessage(ScanTaskPayload payload, int retryCount) {
        ScanTask retryTask = new ScanTask(
                payload.taskId,
                payload.versionId,
                payload.skillPath,
                null, // publisherId not needed for retry
                System.currentTimeMillis(),
                Map.of("retryCount", String.valueOf(retryCount))
        );
        scanTaskProducer.publishScanTask(retryTask);
        log.info("Republished scan task: taskId={}, retryCount={}", payload.taskId, retryCount);
    }

    private void cleanupTempPath(String skillPath) {
        try {
            Path path = Paths.get(skillPath);
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
                log.debug("Cleaned up temp directory: {}", skillPath);
            } else if (Files.exists(path)) {
                Files.delete(path);
                log.debug("Cleaned up temp file: {}", skillPath);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup temp path: {}", skillPath, e);
        }
    }

    /**
     * Scan task payload
     */
    protected record ScanTaskPayload(
            String taskId,
            Long versionId,
            String skillPath
    ) {
    }
}
