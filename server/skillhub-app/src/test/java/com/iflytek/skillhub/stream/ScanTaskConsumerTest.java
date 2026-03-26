package com.iflytek.skillhub.stream;

import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityScanRequest;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.security.SecurityVerdict;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.storage.ObjectMetadata;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScanTaskConsumerTest {
    private static final Path SCAN_TEMP_DIR = Path.of("/tmp/skillhub-scans");

    @Test
    void processBusiness_andMarkCompleted_updatesAuditAndCleansTempDirectory() throws Exception {
        StubSecurityScanner securityScanner = new StubSecurityScanner();
        securityScanner.response = new SecurityScanResponse(
                "scan-1",
                SecurityVerdict.DANGEROUS,
                1,
                "HIGH",
                List.of(),
                1.0
        );
        StubSecurityScanService securityScanService = new StubSecurityScanService();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                securityScanner,
                securityScanService,
                new InMemorySkillVersionRepository(),
                new InMemoryScanTaskProducer(),
                new InMemoryObjectStorageService()
        );
        Files.createDirectories(SCAN_TEMP_DIR);
        Path tempDir = Files.createTempDirectory(SCAN_TEMP_DIR, "scan-task-consumer-success");
        Files.writeString(tempDir.resolve("README.md"), "# demo");
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload(
                "task-1",
                42L,
                tempDir.toString(),
                null,
                ScannerType.SKILL_SCANNER
        );

        consumer.invokeProcessBusiness(payload);
        consumer.invokeMarkCompleted(payload);

        assertThat(securityScanner.lastRequest).isEqualTo(new SecurityScanRequest(
                "task-1",
                42L,
                tempDir.toString(),
                Map.of()
        ));
        assertThat(securityScanService.lastVersionId).isEqualTo(42L);
        assertThat(securityScanService.lastScannerType).isEqualTo(ScannerType.SKILL_SCANNER);
        assertThat(securityScanService.lastResponse).isEqualTo(securityScanner.response);
        assertThat(Files.exists(tempDir)).isFalse();
    }

    @Test
    void markFailed_setsScanFailedWithoutChangingReviewTaskAndCleansTempFile() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setField(version, "id", 42L);
        version.setStatus(SkillVersionStatus.SCANNING);

        InMemorySkillVersionRepository skillVersionRepository = new InMemorySkillVersionRepository(version);
        InMemoryReviewTaskRepository reviewTaskRepository = new InMemoryReviewTaskRepository();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                new StubSecurityScanner(),
                new StubSecurityScanService(),
                skillVersionRepository,
                new InMemoryScanTaskProducer(),
                new InMemoryObjectStorageService()
        );
        Files.createDirectories(SCAN_TEMP_DIR);
        Path tempFile = Files.createTempFile(SCAN_TEMP_DIR, "scan-task-consumer-failure", ".zip");
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload(
                "task-2",
                42L,
                tempFile.toString(),
                null,
                ScannerType.SKILL_SCANNER
        );

        consumer.invokeMarkFailed(payload, "scan failed");

        assertThat(skillVersionRepository.savedVersion.getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
        assertThat(reviewTaskRepository.savedTask).isNull();
        assertThat(reviewTaskRepository.deletedTask).isNull();
        assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    void retryMessage_republishesTaskWithRetryCount() {
        InMemoryScanTaskProducer producer = new InMemoryScanTaskProducer();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                new StubSecurityScanner(),
                new StubSecurityScanService(),
                new InMemorySkillVersionRepository(),
                producer,
                new InMemoryObjectStorageService()
        );
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload(
                "task-3",
                77L,
                "/tmp/retry",
                null,
                ScannerType.SKILL_SCANNER
        );

        consumer.invokeRetryMessage(payload, 2);

        assertThat(producer.publishedTask).isEqualTo(new ScanTask(
                "task-3",
                77L,
                "/tmp/retry",
                null,
                null,
                producer.publishedTask.createdAtMillis(),
                Map.of(
                        "retryCount", "2",
                        "scannerType", ScannerType.SKILL_SCANNER.getValue()
                )
        ));
    }

    @Test
    void processBusiness_withBundleKey_downloadsPackageFromObjectStorageAndCleansTempFile() throws Exception {
        byte[] packageBytes = "zip-bytes".getBytes();
        InMemoryObjectStorageService objectStorageService = new InMemoryObjectStorageService(Map.of(
                "packages/8/42/bundle.zip", packageBytes
        ));
        StubSecurityScanner securityScanner = new StubSecurityScanner();
        securityScanner.response = new SecurityScanResponse(
                "scan-4",
                SecurityVerdict.SAFE,
                0,
                "LOW",
                List.of(),
                0.4
        );
        StubSecurityScanService securityScanService = new StubSecurityScanService();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                securityScanner,
                securityScanService,
                new InMemorySkillVersionRepository(),
                new InMemoryScanTaskProducer(),
                objectStorageService
        );
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload(
                "task-4",
                42L,
                null,
                "packages/8/42/bundle.zip",
                ScannerType.SKILL_SCANNER
        );

        consumer.invokeProcessBusiness(payload);

        Path downloadedPackage = Path.of(securityScanner.lastRequest.skillPackagePath());
        assertThat(downloadedPackage).startsWith(SCAN_TEMP_DIR);
        assertThat(Files.readAllBytes(downloadedPackage)).isEqualTo(packageBytes);
        assertThat(objectStorageService.lastGetKey).isEqualTo("packages/8/42/bundle.zip");

        consumer.invokeMarkCompleted(payload);

        assertThat(Files.exists(downloadedPackage)).isFalse();
    }

    @Test
    void handleMessage_retryableScannerFailureWithBundleKey_requeuesTaskAndCleansStagedTempFile() throws Exception {
        long versionId = 42424242L;
        byte[] packageBytes = "zip-bytes".getBytes();
        InMemoryObjectStorageService objectStorageService = new InMemoryObjectStorageService(Map.of(
                "packages/8/42424242/bundle.zip", packageBytes
        ));
        deleteScanTempFiles(versionId);
        StubSecurityScanner securityScanner = new StubSecurityScanner();
        securityScanner.failure = new IllegalStateException("scanner unavailable");
        InMemoryScanTaskProducer producer = new InMemoryScanTaskProducer();
        InMemorySkillVersionRepository repository = new InMemorySkillVersionRepository();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                securityScanner,
                new StubSecurityScanService(),
                repository,
                producer,
                objectStorageService
        );

        consumer.handleMessage(new StreamMessageId(9, 0), Map.of(
                "taskId", "task-5",
                "versionId", String.valueOf(versionId),
                "bundleKey", "packages/8/42424242/bundle.zip",
                "scannerType", ScannerType.SKILL_SCANNER.getValue()
        ));

        assertThat(producer.publishedTask).isEqualTo(new ScanTask(
                "task-5",
                versionId,
                null,
                "packages/8/42424242/bundle.zip",
                null,
                producer.publishedTask.createdAtMillis(),
                Map.of(
                        "retryCount", "1",
                        "scannerType", ScannerType.SKILL_SCANNER.getValue()
                )
        ));
        assertThat(repository.savedVersion).isNull();
        assertThat(listScanTempFiles(versionId)).isEmpty();
    }

    @Test
    void handleMessage_retryableBundleDownloadFailure_requeuesTaskWithoutLeakingTempFile() throws Exception {
        long versionId = 43434343L;
        InMemoryObjectStorageService objectStorageService = new InMemoryObjectStorageService();
        objectStorageService.getFailure = new IllegalStateException("missing bundle");
        deleteScanTempFiles(versionId);
        InMemoryScanTaskProducer producer = new InMemoryScanTaskProducer();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                new StubSecurityScanner(),
                new StubSecurityScanService(),
                new InMemorySkillVersionRepository(),
                producer,
                objectStorageService
        );

        consumer.handleMessage(new StreamMessageId(10, 0), Map.of(
                "taskId", "task-6",
                "versionId", String.valueOf(versionId),
                "bundleKey", "packages/8/43434343/bundle.zip",
                "scannerType", ScannerType.SKILL_SCANNER.getValue()
        ));

        assertThat(producer.publishedTask.bundleKey()).isEqualTo("packages/8/43434343/bundle.zip");
        assertThat(producer.publishedTask.metadata()).containsEntry("retryCount", "1");
        assertThat(listScanTempFiles(versionId)).isEmpty();
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private List<Path> listScanTempFiles(long versionId) throws IOException {
        Files.createDirectories(SCAN_TEMP_DIR);
        try (var stream = Files.list(SCAN_TEMP_DIR)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith(versionId + "-"))
                    .toList();
        }
    }

    private void deleteScanTempFiles(long versionId) throws IOException {
        for (Path path : listScanTempFiles(versionId)) {
            Files.deleteIfExists(path);
        }
    }

    private static final class TestableScanTaskConsumer extends ScanTaskConsumer {
        private final RStream<String, String> stream;

        @SuppressWarnings("unchecked")
        private TestableScanTaskConsumer(SecurityScanner securityScanner,
                                         SecurityScanService securityScanService,
                                         SkillVersionRepository skillVersionRepository,
                                         ScanTaskProducer scanTaskProducer,
                                         ObjectStorageService objectStorageService) {
            super(
                    mock(RedissonClient.class),
                    "skillhub:scan:requests",
                    "skillhub-scanners",
                    securityScanner,
                    securityScanService,
                    skillVersionRepository,
                    scanTaskProducer,
                    objectStorageService
            );
            this.stream = mock(RStream.class);
        }

        @Override
        protected RStream<String, String> createStream() {
            return stream;
        }

        private void invokeProcessBusiness(ScanTaskPayload payload) {
            processBusiness(payload);
        }

        private void invokeMarkCompleted(ScanTaskPayload payload) {
            markCompleted(payload);
        }

        private void invokeMarkFailed(ScanTaskPayload payload, String error) {
            markFailed(payload, error);
        }

        private void invokeRetryMessage(ScanTaskPayload payload, int retryCount) {
            retryMessage(payload, retryCount);
        }
    }

    private static final class StubSecurityScanner implements SecurityScanner {
        private SecurityScanRequest lastRequest;
        private SecurityScanResponse response;
        private RuntimeException failure;

        @Override
        public SecurityScanResponse scan(SecurityScanRequest request) {
            this.lastRequest = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public String getScannerType() {
            return "skill-scanner";
        }
    }

    private static final class StubSecurityScanService extends SecurityScanService {
        private Long lastVersionId;
        private ScannerType lastScannerType;
        private SecurityScanResponse lastResponse;

        private StubSecurityScanService() {
            super(null, null, task -> {
            }, new com.fasterxml.jackson.databind.ObjectMapper(), "local", true);
        }

        @Override
        public void processScanResult(Long versionId, ScannerType scannerType, SecurityScanResponse response) {
            this.lastVersionId = versionId;
            this.lastScannerType = scannerType;
            this.lastResponse = response;
        }
    }

    private static final class InMemorySkillVersionRepository implements SkillVersionRepository {
        private final SkillVersion version;
        private SkillVersion savedVersion;

        private InMemorySkillVersionRepository() {
            this.version = null;
        }

        private InMemorySkillVersionRepository(SkillVersion version) {
            this.version = version;
        }

        @Override
        public Optional<SkillVersion> findById(Long id) {
            return version != null && id.equals(version.getId()) ? Optional.of(version) : Optional.empty();
        }

        @Override
        public List<SkillVersion> findByIdIn(List<Long> ids) {
            throw unsupported();
        }

        @Override
        public List<SkillVersion> findBySkillIdIn(List<Long> skillIds) {
            throw unsupported();
        }

        @Override
        public List<SkillVersion> findBySkillIdInAndStatus(List<Long> skillIds, SkillVersionStatus status) {
            throw unsupported();
        }

        @Override
        public List<SkillVersion> findBySkillId(Long skillId) {
            throw unsupported();
        }

        @Override
        public Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version) {
            throw unsupported();
        }

        @Override
        public List<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status) {
            throw unsupported();
        }

        @Override
        public SkillVersion save(SkillVersion version) {
            this.savedVersion = version;
            return version;
        }

        @Override
        public void delete(SkillVersion version) {
            throw unsupported();
        }

        @Override
        public void flush() {
        }

        @Override
        public void deleteBySkillId(Long skillId) {
            throw unsupported();
        }
    }

    private static final class InMemoryReviewTaskRepository implements ReviewTaskRepository {
        private ReviewTask savedTask;
        private ReviewTask deletedTask;

        @Override
        public ReviewTask save(ReviewTask reviewTask) {
            this.savedTask = reviewTask;
            return reviewTask;
        }

        @Override
        public Optional<ReviewTask> findById(Long id) {
            throw unsupported();
        }

        @Override
        public Optional<ReviewTask> findBySkillVersionIdAndStatus(Long skillVersionId, ReviewTaskStatus status) {
            throw unsupported();
        }

        @Override
        public org.springframework.data.domain.Page<ReviewTask> findByStatus(ReviewTaskStatus status,
                                                                             org.springframework.data.domain.Pageable pageable) {
            throw unsupported();
        }

        @Override
        public org.springframework.data.domain.Page<ReviewTask> findByNamespaceIdAndStatus(Long namespaceId,
                                                                                            ReviewTaskStatus status,
                                                                                            org.springframework.data.domain.Pageable pageable) {
            throw unsupported();
        }

        @Override
        public org.springframework.data.domain.Page<ReviewTask> findBySubmittedByAndStatus(String submittedBy,
                                                                                           ReviewTaskStatus status,
                                                                                           org.springframework.data.domain.Pageable pageable) {
            throw unsupported();
        }

        @Override
        public void deleteBySkillVersionIdIn(Collection<Long> skillVersionIds) {
            throw unsupported();
        }

        @Override
        public void delete(ReviewTask reviewTask) {
            this.deletedTask = reviewTask;
        }

        @Override
        public int updateStatusWithVersion(Long id, ReviewTaskStatus status, String reviewedBy,
                                           String reviewComment, Integer expectedVersion) {
            throw unsupported();
        }
    }

    private static final class InMemoryScanTaskProducer implements ScanTaskProducer {
        private ScanTask publishedTask;

        @Override
        public void publishScanTask(ScanTask task) {
            this.publishedTask = task;
        }
    }

    private static final class InMemoryObjectStorageService implements ObjectStorageService {
        private final Map<String, byte[]> objects;
        private String lastGetKey;
        private RuntimeException getFailure;

        private InMemoryObjectStorageService() {
            this(Map.of());
        }

        private InMemoryObjectStorageService(Map<String, byte[]> objects) {
            this.objects = new java.util.HashMap<>(objects);
        }

        @Override
        public void putObject(String key, InputStream data, long size, String contentType) {
            throw unsupported();
        }

        @Override
        public InputStream getObject(String key) {
            lastGetKey = key;
            if (getFailure != null) {
                throw getFailure;
            }
            byte[] content = objects.get(key);
            if (content == null) {
                throw new IllegalStateException("Missing object: " + key);
            }
            return new ByteArrayInputStream(content);
        }

        @Override
        public void deleteObject(String key) {
            throw unsupported();
        }

        @Override
        public void deleteObjects(List<String> keys) {
            throw unsupported();
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public ObjectMetadata getMetadata(String key) {
            byte[] content = objects.get(key);
            if (content == null) {
                throw new IllegalStateException("Missing object: " + key);
            }
            return new ObjectMetadata(content.length, "application/zip", Instant.now());
        }

        @Override
        public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
            throw unsupported();
        }
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }
}
