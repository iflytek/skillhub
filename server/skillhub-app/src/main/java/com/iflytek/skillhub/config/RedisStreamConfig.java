package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.storage.ObjectStorageService;
import com.iflytek.skillhub.stream.RedissonScanTaskProducer;
import com.iflytek.skillhub.stream.ScanTaskConsumer;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
public class RedisStreamConfig {

    @Value("${skillhub.security.stream.key:skillhub:scan:requests}")
    private String streamKey;

    @Value("${skillhub.security.stream.group:skillhub-scanners}")
    private String groupName;

    @Value("${skillhub.security.stream.reclaim-enabled:true}")
    private boolean reclaimEnabled;

    @Value("${skillhub.security.stream.reclaim-min-idle:PT2M}")
    private Duration reclaimMinIdle;

    @Value("${skillhub.security.stream.reclaim-batch-size:20}")
    private int reclaimBatchSize;

    @Value("${skillhub.security.stream.reclaim-interval:PT30S}")
    private Duration reclaimInterval;

    @Bean
    public RedissonScanTaskProducer redisScanTaskProducer(RedissonClient redissonClient) {
        return new RedissonScanTaskProducer(redissonClient, streamKey);
    }

    @Bean
    public ScanTaskConsumer scanTaskConsumer(RedissonClient redissonClient,
                                             SecurityScanner securityScanner,
                                             SecurityScanService securityScanService,
                                             SkillVersionRepository skillVersionRepository,
                                             ScanTaskProducer scanTaskProducer,
                                             ObjectStorageService objectStorageService) {
        return new ScanTaskConsumer(
                redissonClient,
                streamKey,
                groupName,
                securityScanner,
                securityScanService,
                skillVersionRepository,
                scanTaskProducer,
                objectStorageService,
                reclaimEnabled,
                reclaimMinIdle,
                reclaimBatchSize,
                reclaimInterval
        );
    }
}
