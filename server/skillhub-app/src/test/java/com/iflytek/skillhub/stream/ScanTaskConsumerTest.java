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
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
                new InMemorySkillRepository(),
                new InMemoryReviewTaskRepository(),
                new InMemoryScanTaskProducer()
        );
        Files.createDirectories(SCAN_TEMP_DIR);
        Path tempDir = Files.createTempDirectory(SCAN_TEMP_DIR, "scan-task-consumer-success");
        Files.writeString(tempDir.resolve("README.md"), "# demo");
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload("task-1", 42L, tempDir.toString(), ScannerType.SKILL_SCANNER);

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
    void markFailed_setsScanFailedCreatesReviewTaskAndCleansTempFile() throws Exception {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setField(version, "id", 42L);
        version.setStatus(SkillVersionStatus.SCANNING);

        Skill skill = new Skill(20L, "demo-skill", "publisher-1", SkillVisibility.PUBLIC);
        setField(skill, "id", 8L);

        InMemorySkillVersionRepository skillVersionRepository = new InMemorySkillVersionRepository(version);
        InMemorySkillRepository skillRepository = new InMemorySkillRepository(skill);
        InMemoryReviewTaskRepository reviewTaskRepository = new InMemoryReviewTaskRepository();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                new StubSecurityScanner(),
                new StubSecurityScanService(),
                skillVersionRepository,
                skillRepository,
                reviewTaskRepository,
                new InMemoryScanTaskProducer()
        );
        Files.createDirectories(SCAN_TEMP_DIR);
        Path tempFile = Files.createTempFile(SCAN_TEMP_DIR, "scan-task-consumer-failure", ".zip");
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload("task-2", 42L, tempFile.toString(), ScannerType.SKILL_SCANNER);

        consumer.invokeMarkFailed(payload, "scan failed");

        assertThat(skillVersionRepository.savedVersion.getStatus()).isEqualTo(SkillVersionStatus.SCAN_FAILED);
        assertThat(reviewTaskRepository.savedTask).isNotNull();
        assertThat(reviewTaskRepository.savedTask.getSkillVersionId()).isEqualTo(42L);
        assertThat(reviewTaskRepository.savedTask.getNamespaceId()).isEqualTo(20L);
        assertThat(reviewTaskRepository.savedTask.getStatus()).isEqualTo(ReviewTaskStatus.PENDING);
        assertThat(Files.exists(tempFile)).isFalse();
    }

    @Test
    void retryMessage_republishesTaskWithRetryCount() {
        InMemoryScanTaskProducer producer = new InMemoryScanTaskProducer();
        TestableScanTaskConsumer consumer = new TestableScanTaskConsumer(
                new StubSecurityScanner(),
                new StubSecurityScanService(),
                new InMemorySkillVersionRepository(),
                new InMemorySkillRepository(),
                new InMemoryReviewTaskRepository(),
                producer
        );
        ScanTaskConsumer.ScanTaskPayload payload = new ScanTaskConsumer.ScanTaskPayload("task-3", 77L, "/tmp/retry", ScannerType.SKILL_SCANNER);

        consumer.invokeRetryMessage(payload, 2);

        assertThat(producer.publishedTask).isEqualTo(new ScanTask(
                "task-3",
                77L,
                "/tmp/retry",
                null,
                producer.publishedTask.createdAtMillis(),
                Map.of("retryCount", "2")
        ));
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestableScanTaskConsumer extends ScanTaskConsumer {
        private TestableScanTaskConsumer(SecurityScanner securityScanner,
                                         SecurityScanService securityScanService,
                                         SkillVersionRepository skillVersionRepository,
                                         SkillRepository skillRepository,
                                         ReviewTaskRepository reviewTaskRepository,
                                         ScanTaskProducer scanTaskProducer) {
            super(
                    null,
                    "skillhub:scan:requests",
                    "skillhub-scanners",
                    securityScanner,
                    securityScanService,
                    skillVersionRepository,
                    skillRepository,
                    reviewTaskRepository,
                    scanTaskProducer
            );
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

        @Override
        public SecurityScanResponse scan(SecurityScanRequest request) {
            this.lastRequest = request;
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
            }, event -> {
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
        public void deleteBySkillId(Long skillId) {
            throw unsupported();
        }
    }

    private static final class InMemorySkillRepository implements SkillRepository {
        private final Skill skill;

        private InMemorySkillRepository() {
            this.skill = null;
        }

        private InMemorySkillRepository(Skill skill) {
            this.skill = skill;
        }

        @Override
        public Optional<Skill> findById(Long id) {
            return skill != null && id.equals(skill.getId()) ? Optional.of(skill) : Optional.empty();
        }

        @Override
        public List<Skill> findByIdIn(List<Long> ids) {
            throw unsupported();
        }

        @Override
        public List<Skill> findAll() {
            throw unsupported();
        }

        @Override
        public List<Skill> findByNamespaceIdAndSlug(Long namespaceId, String slug) {
            throw unsupported();
        }

        @Override
        public Optional<Skill> findByNamespaceIdAndSlugAndOwnerId(Long namespaceId, String slug, String ownerId) {
            throw unsupported();
        }

        @Override
        public List<Skill> findByNamespaceIdAndStatus(Long namespaceId, com.iflytek.skillhub.domain.skill.SkillStatus status) {
            throw unsupported();
        }

        @Override
        public Skill save(Skill skill) {
            throw unsupported();
        }

        @Override
        public void flush() {
            throw unsupported();
        }

        @Override
        public void delete(Skill skill) {
            throw unsupported();
        }

        @Override
        public List<Skill> findByOwnerId(String ownerId) {
            throw unsupported();
        }

        @Override
        public org.springframework.data.domain.Page<Skill> findByOwnerId(String ownerId,
                                                                         org.springframework.data.domain.Pageable pageable) {
            throw unsupported();
        }

        @Override
        public void incrementDownloadCount(Long skillId) {
            throw unsupported();
        }

        @Override
        public List<Skill> findBySlug(String slug) {
            throw unsupported();
        }

        @Override
        public Optional<Skill> findByNamespaceSlugAndSlug(String namespaceSlug, String slug) {
            throw unsupported();
        }
    }

    private static final class InMemoryReviewTaskRepository implements ReviewTaskRepository {
        private ReviewTask savedTask;

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
            throw unsupported();
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

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException();
    }
}
