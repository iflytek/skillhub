package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityScanRequest;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.observability.MessageObservationSupport;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

public class ScanTaskConsumer extends AbstractStreamConsumer<ScanTaskConsumer.ScanTaskPayload> {
    private static final Path SCAN_TEMP_DIR = Paths.get("/tmp/skillhub-scans").toAbsolutePath().normalize();

    private final RedissonClient redissonClient;
    private final SecurityScanner securityScanner;
    private final SecurityScanService securityScanService;
    private final SkillVersionRepository skillVersionRepository;
    private final ScanTaskProducer scanTaskProducer;
    private final ObjectStorageService objectStorageService;

    public ScanTaskConsumer(RedissonClient redissonClient,
                            String streamKey,
                            String groupName,
                            SecurityScanner securityScanner,
                            SecurityScanService securityScanService,
                            SkillVersionRepository skillVersionRepository,
                            ScanTaskProducer scanTaskProducer,
                            ObjectStorageService objectStorageService,
                            MessageObservationSupport messageObservationSupport) {
        super(redissonClient, streamKey, groupName, messageObservationSupport);
        this.redissonClient = redissonClient;
        this.securityScanner = securityScanner;
        this.securityScanService = securityScanService;
        this.skillVersionRepository = skillVersionRepository;
        this.scanTaskProducer = scanTaskProducer;
        this.objectStorageService = objectStorageService;
    }

    public ScanTaskConsumer(RedissonClient redissonClient,
                            String streamKey,
                            String groupName,
                            SecurityScanner securityScanner,
                            SecurityScanService securityScanService,
                            SkillVersionRepository skillVersionRepository,
                            ScanTaskProducer scanTaskProducer,
                            ObjectStorageService objectStorageService,
                            boolean reclaimEnabled,
                            Duration reclaimMinIdle,
                            int reclaimBatchSize,
                            Duration reclaimInterval,
                            MessageObservationSupport messageObservationSupport) {
        super(
                redissonClient,
                streamKey,
                groupName,
                reclaimEnabled,
                reclaimMinIdle,
                reclaimBatchSize,
                reclaimInterval,
                messageObservationSupport
        );
        this.redissonClient = redissonClient;
        this.securityScanner = securityScanner;
        this.securityScanService = securityScanService;
        this.skillVersionRepository = skillVersionRepository;
        this.scanTaskProducer = scanTaskProducer;
        this.objectStorageService = objectStorageService;
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
                    blankToNull(data.get("skillPath")),
                    blankToNull(data.get("bundleKey")),
                    scannerType,
                    parseRetryCount(data)
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(ScanTaskPayload payload) {
        return "taskId=" + payload.taskId() + ", versionId=" + payload.versionId() + ", scanner=" + payload.scannerType();
    }

    @Override
    protected void markProcessing(ScanTaskPayload payload) {
        log.info("Processing security scan task: taskId={}, versionId={}, scanner={}, retryCount={}, source={}",
                payload.taskId(),
                payload.versionId(),
                payload.scannerType(),
                payload.retryCount(),
                payload.sourceDescription());
    }

    @Override
    protected void processBusiness(ScanTaskPayload payload) {
        if (securityScanService.isTaskAlreadyProcessed(payload.taskId())) {
            log.info("Skipping already processed security scan task: taskId={}, versionId={}", payload.taskId(), payload.versionId());
            return;
        }
        RLock processingLock = redissonClient.getLock("skillhub:scan:processing:" + payload.taskId());
        boolean acquired = false;
        try {
            acquired = processingLock.tryLock();
            if (!acquired) {
                log.info("Skipping concurrently processed security scan task: taskId={}, versionId={}",
                        payload.taskId(), payload.versionId());
                payload.skipCleanup();
                // A normal return is treated as success by AbstractStreamConsumer and ACKs
                // the Redis entry. Requeue through the common failure path instead, so a
                // reclaimed duplicate cannot erase the only durable delivery while the active
                // scanner still owns the task lock.
                throw new ConcurrentScanInProgressException(payload.taskId());
            }
            if (securityScanService.isTaskAlreadyProcessed(payload.taskId())) {
                return;
            }
            executeScan(payload);
        } finally {
            if (acquired && processingLock.isHeldByCurrentThread()) {
                processingLock.unlock();
            }
        }
    }

    private void executeScan(ScanTaskPayload payload) {
        String skillPath = resolveWorkingSkillPath(payload);
        SecurityScanRequest request = new SecurityScanRequest(
                payload.taskId(), payload.versionId(), skillPath, Map.of());
        SecurityScanResponse response = securityScanner.scan(request);
        securityScanService.processScanResult(payload.versionId(), payload.scannerType(), response);
    }

    private static final class ConcurrentScanInProgressException extends RuntimeException {
        private ConcurrentScanInProgressException(String taskId) {
            super("Security scan is already in progress: taskId=" + taskId);
        }
    }

    @Override
    protected void markCompleted(ScanTaskPayload payload) {
        cleanupTempPath(payload.cleanupPath());
    }

    @Override
    protected void markFailed(ScanTaskPayload payload, String error) {
        log.error("Security scan task failed permanently: taskId={}, versionId={}, scanner={}, source={}, error={}",
                payload.taskId(),
                payload.versionId(),
                payload.scannerType(),
                payload.sourceDescription(),
                error);
        try {
            skillVersionRepository.findById(payload.versionId())
                    .filter(version -> version.getStatus() == SkillVersionStatus.SCANNING)
                    .ifPresent(version -> {
                        version.setStatus(SkillVersionStatus.SCAN_FAILED);
                        skillVersionRepository.save(version);
                    });
        } finally {
            cleanupTempPath(payload.cleanupPath());
        }
    }

    @Override
    protected void retryMessage(ScanTaskPayload payload, int retryCount) {
        log.warn("Retrying security scan task: taskId={}, versionId={}, scanner={}, nextRetryCount={}, source={}",
                payload.taskId(),
                payload.versionId(),
                payload.scannerType(),
                retryCount,
                payload.sourceDescription());
        cleanupRetryTempPath(payload);
        scanTaskProducer.publishScanTask(new ScanTask(
                payload.taskId(),
                payload.versionId(),
                payload.skillPath(),
                payload.bundleKey(),
                null,
                System.currentTimeMillis(),
                Map.of(
                        "retryCount", String.valueOf(retryCount),
                        "scannerType", payload.scannerType().getValue()
                )
        ));
    }

    private String resolveWorkingSkillPath(ScanTaskPayload payload) {
        if (payload.bundleKey() == null) {
            if (payload.skillPath() == null || payload.skillPath().isBlank()) {
                throw new IllegalStateException("Security scan task missing skillPath and bundleKey");
            }
            payload.markWorkingSkillPath(payload.skillPath());
            return payload.skillPath();
        }

        try {
            Files.createDirectories(SCAN_TEMP_DIR);
            Path tempFile = Files.createTempFile(SCAN_TEMP_DIR, payload.versionId() + "-", ".zip");
            payload.markWorkingSkillPath(tempFile.toString());
            try (InputStream inputStream = objectStorageService.getObject(payload.bundleKey())) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Staged security scan bundle: taskId={}, versionId={}, bundleKey={}, tempPath={}",
                    payload.taskId(), payload.versionId(), payload.bundleKey(), tempFile);
            return tempFile.toString();
        } catch (Exception e) {
            log.error("Failed to stage security scan bundle: taskId={}, versionId={}, bundleKey={}",
                    payload.taskId(), payload.versionId(), payload.bundleKey(), e);
            cleanupTempPath(payload.workingSkillPath());
            throw new IllegalStateException("Failed to stage scan bundle: " + payload.bundleKey(), e);
        }
    }

    private void cleanupRetryTempPath(ScanTaskPayload payload) {
        if (payload.bundleKey() != null) {
            cleanupTempPath(payload.workingSkillPath());
        }
    }

    private void cleanupTempPath(String skillPath) {
        if (skillPath == null || skillPath.isBlank()) {
            return;
        }
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    protected static final class ScanTaskPayload {
        private final String taskId;
        private final Long versionId;
        private final String skillPath;
        private final String bundleKey;
        private final ScannerType scannerType;
        private final int retryCount;
        private String workingSkillPath;
        private boolean cleanupEnabled = true;

        protected ScanTaskPayload(String taskId, Long versionId, String skillPath, String bundleKey, ScannerType scannerType) {
            this(taskId, versionId, skillPath, bundleKey, scannerType, 0);
        }

        protected ScanTaskPayload(String taskId,
                                  Long versionId,
                                  String skillPath,
                                  String bundleKey,
                                  ScannerType scannerType,
                                  int retryCount) {
            this.taskId = taskId;
            this.versionId = versionId;
            this.skillPath = skillPath;
            this.bundleKey = bundleKey;
            this.scannerType = scannerType;
            this.retryCount = retryCount;
        }

        protected String taskId() {
            return taskId;
        }

        protected Long versionId() {
            return versionId;
        }

        protected String skillPath() {
            return skillPath;
        }

        protected String bundleKey() {
            return bundleKey;
        }

        protected ScannerType scannerType() {
            return scannerType;
        }

        protected int retryCount() {
            return retryCount;
        }

        protected void markWorkingSkillPath(String workingSkillPath) {
            this.workingSkillPath = workingSkillPath;
        }

        protected String cleanupPath() {
            if (!cleanupEnabled) {
                return null;
            }
            return workingSkillPath != null ? workingSkillPath : skillPath;
        }

        protected void skipCleanup() {
            cleanupEnabled = false;
        }

        protected String workingSkillPath() {
            return workingSkillPath;
        }

        protected String sourceDescription() {
            if (bundleKey != null) {
                return "bundleKey:" + bundleKey;
            }
            return "skillPath:" + skillPath;
        }
    }
}
