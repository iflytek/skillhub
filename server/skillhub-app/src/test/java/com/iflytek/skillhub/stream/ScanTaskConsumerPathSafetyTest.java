package com.iflytek.skillhub.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class ScanTaskConsumerPathSafetyTest {

    @Test
    void cleanupTempPath_ignoresPathsOutsideScanTempDirectory() throws Exception {
        ScanTaskConsumer consumer = new ScanTaskConsumer(
                org.mockito.Mockito.mock(RedisConnectionFactory.class),
                "scan-stream",
                "scan-group",
                org.mockito.Mockito.mock(SecurityScanner.class),
                org.mockito.Mockito.mock(SecurityScanService.class),
                org.mockito.Mockito.mock(SkillVersionRepository.class),
                org.mockito.Mockito.mock(SkillRepository.class),
                org.mockito.Mockito.mock(ReviewTaskRepository.class),
                org.mockito.Mockito.mock(ScanTaskProducer.class)
        );
        Path outsideFile = Files.createTempFile("scan-cleanup-", ".txt");
        Files.writeString(outsideFile, "keep");

        Method cleanup = ScanTaskConsumer.class.getDeclaredMethod("cleanupTempPath", String.class);
        cleanup.setAccessible(true);
        cleanup.invoke(consumer, outsideFile.toString());

        assertThat(Files.exists(outsideFile)).isTrue();
        Files.deleteIfExists(outsideFile);
    }
}
