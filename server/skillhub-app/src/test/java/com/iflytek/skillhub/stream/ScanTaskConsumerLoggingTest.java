package com.iflytek.skillhub.stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.ScannerType;
import com.iflytek.skillhub.domain.security.SecurityScanResponse;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionStatus;
import com.iflytek.skillhub.storage.ObjectMetadata;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScanTaskConsumerLoggingTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(TestableLoggingConsumer.class);
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void handleMessage_logsBundleTaskStartAndRetry() {
        TestProducer producer = new TestProducer();
        TestableLoggingConsumer consumer = new TestableLoggingConsumer(
                new FailingScanner(new IllegalStateException("scanner unavailable")),
                new NoOpSecurityScanService(),
                new EmptySkillVersionRepository(),
                producer,
                new InMemoryObjectStorageService(Map.of("packages/8/42/bundle.zip", "zip".getBytes()))
        );
        attachAppender();

        consumer.handleMessage(new StreamMessageId(1, 0), Map.of(
                "taskId", "task-1",
                "versionId", "42",
                "bundleKey", "packages/8/42/bundle.zip",
                "scannerType", ScannerType.SKILL_SCANNER.getValue()
        ));

        assertThat(loggedMessages()).anyMatch(message -> message.contains(
                "Processing security scan task: taskId=task-1, versionId=42, scanner=SKILL_SCANNER, retryCount=0, source=bundleKey:packages/8/42/bundle.zip"
        ));
        assertThat(loggedMessages()).anyMatch(message -> message.contains(
                "Retrying security scan task: taskId=task-1, versionId=42, scanner=SKILL_SCANNER, nextRetryCount=1"
        ));
        assertThat(producer.lastMetadata).containsEntry("retryCount", "1");
    }

    @Test
    void handleMessage_logsBundleStageFailureBeforeRetry() {
        TestableLoggingConsumer consumer = new TestableLoggingConsumer(
                new FailingScanner(null),
                new NoOpSecurityScanService(),
                new EmptySkillVersionRepository(),
                new TestProducer(),
                new InMemoryObjectStorageService(new IllegalStateException("bundle missing"))
        );
        attachAppender();

        consumer.handleMessage(new StreamMessageId(2, 0), Map.of(
                "taskId", "task-2",
                "versionId", "43",
                "bundleKey", "packages/8/43/bundle.zip",
                "scannerType", ScannerType.SKILL_SCANNER.getValue()
        ));

        assertThat(loggedMessages()).anyMatch(message -> message.contains(
                "Failed to stage security scan bundle: taskId=task-2, versionId=43, bundleKey=packages/8/43/bundle.zip"
        ));
    }

    @Test
    void handleMessage_logsFinalFailureAfterRetriesExhausted() {
        SkillVersion version = new SkillVersion(8L, "1.0.0", "publisher-1");
        setVersionId(version, 44L);
        version.setStatus(SkillVersionStatus.SCANNING);
        TestableLoggingConsumer consumer = new TestableLoggingConsumer(
                new FailingScanner(new IllegalStateException("scanner unavailable")),
                new NoOpSecurityScanService(),
                new SingleSkillVersionRepository(version),
                new TestProducer(),
                new InMemoryObjectStorageService(Map.of("packages/8/44/bundle.zip", "zip".getBytes()))
        );
        attachAppender();

        consumer.handleMessage(new StreamMessageId(3, 0), Map.of(
                "taskId", "task-3",
                "versionId", "44",
                "bundleKey", "packages/8/44/bundle.zip",
                "scannerType", ScannerType.SKILL_SCANNER.getValue(),
                "retryCount", "3"
        ));

        assertThat(loggedMessages()).anyMatch(message -> message.contains(
                "Security scan task failed permanently: taskId=task-3, versionId=44, scanner=SKILL_SCANNER"
        ));
    }

    private void attachAppender() {
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    private List<String> loggedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void setVersionId(SkillVersion version, Long id) {
        try {
            java.lang.reflect.Field field = SkillVersion.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(version, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TestableLoggingConsumer extends ScanTaskConsumer {
        private final RStream<String, String> stream = mock(RStream.class);

        private TestableLoggingConsumer(SecurityScanner securityScanner,
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
        }

        @Override
        protected RStream<String, String> createStream() {
            return stream;
        }
    }

    private static final class FailingScanner implements SecurityScanner {
        private final RuntimeException failure;

        private FailingScanner(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public SecurityScanResponse scan(com.iflytek.skillhub.domain.security.SecurityScanRequest request) {
            if (failure != null) {
                throw failure;
            }
            throw new IllegalStateException("unexpected");
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

    private static final class NoOpSecurityScanService extends SecurityScanService {
        private NoOpSecurityScanService() {
            super(null, null, task -> {}, new ObjectMapper(), "upload", true);
        }

        @Override
        public void processScanResult(Long versionId, ScannerType scannerType, SecurityScanResponse response) {
        }
    }

    private static final class TestProducer implements ScanTaskProducer {
        private Map<String, String> lastMetadata;

        @Override
        public void publishScanTask(com.iflytek.skillhub.domain.security.ScanTask task) {
            this.lastMetadata = task.metadata();
        }
    }

    private static class EmptySkillVersionRepository implements SkillVersionRepository {
        @Override
        public Optional<SkillVersion> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<SkillVersion> findByIdIn(List<Long> ids) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SkillVersion> findBySkillIdIn(List<Long> skillIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SkillVersion> findBySkillIdInAndStatus(List<Long> skillIds, SkillVersionStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SkillVersion> findBySkillId(Long skillId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SkillVersion> findBySkillIdAndVersion(Long skillId, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<SkillVersion> findBySkillIdAndStatus(Long skillId, SkillVersionStatus status) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SkillVersion save(SkillVersion version) {
            return version;
        }

        @Override
        public void delete(SkillVersion version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void flush() {
        }

        @Override
        public void deleteBySkillId(Long skillId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class SingleSkillVersionRepository extends EmptySkillVersionRepository {
        private final SkillVersion version;

        private SingleSkillVersionRepository(SkillVersion version) {
            this.version = version;
        }

        @Override
        public Optional<SkillVersion> findById(Long id) {
            return Optional.of(version);
        }
    }

    private static final class InMemoryObjectStorageService implements ObjectStorageService {
        private final Map<String, byte[]> objects;
        private final RuntimeException failure;

        private InMemoryObjectStorageService(Map<String, byte[]> objects) {
            this.objects = objects;
            this.failure = null;
        }

        private InMemoryObjectStorageService(RuntimeException failure) {
            this.objects = Map.of();
            this.failure = failure;
        }

        @Override
        public void putObject(String key, InputStream data, long size, String contentType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream getObject(String key) {
            if (failure != null) {
                throw failure;
            }
            byte[] content = objects.get(key);
            if (content == null) {
                throw new IllegalStateException("missing: " + key);
            }
            return new ByteArrayInputStream(content);
        }

        @Override
        public void deleteObject(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteObjects(List<String> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public ObjectMetadata getMetadata(String key) {
            byte[] content = objects.get(key);
            return new ObjectMetadata(content.length, "application/zip", Instant.now());
        }

        @Override
        public String generatePresignedUrl(String key, Duration expiry, String downloadFilename) {
            throw new UnsupportedOperationException();
        }
    }
}
