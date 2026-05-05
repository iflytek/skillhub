package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.security.ScanTask;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.stream.RedissonScanTaskProducer;
import com.iflytek.skillhub.stream.ScanTaskConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RedisStreamConfigTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private SecurityScanner securityScanner;

    @Mock
    private SecurityScanService securityScanService;

    @Mock
    private SkillVersionRepository skillVersionRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @Test
    void redisScanTaskProducer_createsProducer() {
        RedisStreamConfig config = new RedisStreamConfig();
        RedissonScanTaskProducer producer = config.redisScanTaskProducer(redissonClient);

        assertThat(producer).isNotNull();
    }

    @Test
    void scanTaskConsumer_createsConsumer() {
        RedisStreamConfig config = new RedisStreamConfig();
        ScanTaskProducer scanTaskProducer = task -> {};

        ScanTaskConsumer consumer = config.scanTaskConsumer(
                redissonClient,
                securityScanner,
                securityScanService,
                skillVersionRepository,
                scanTaskProducer,
                objectStorageService
        );

        assertThat(consumer).isNotNull();
    }

    @Test
    void constructor_createsInstance() {
        RedisStreamConfig config = new RedisStreamConfig();
        assertThat(config).isNotNull();
    }
}
