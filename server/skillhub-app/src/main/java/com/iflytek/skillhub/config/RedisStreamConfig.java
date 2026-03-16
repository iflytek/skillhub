package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.review.ReviewTaskRepository;
import com.iflytek.skillhub.domain.security.ScanTaskProducer;
import com.iflytek.skillhub.domain.security.SecurityScanService;
import com.iflytek.skillhub.domain.security.SecurityScanner;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.stream.RedisScanTaskProducer;
import com.iflytek.skillhub.stream.ScanTaskConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisStreamConfig {

    @Value("${skillhub.security.stream.key:skillhub:scan:requests}")
    private String streamKey;

    @Value("${skillhub.security.stream.group:skillhub-scanners}")
    private String groupName;

    @Bean
    public RedisScanTaskProducer redisScanTaskProducer(StringRedisTemplate redisTemplate) {
        return new RedisScanTaskProducer(redisTemplate, streamKey);
    }

    @Bean
    public ScanTaskConsumer scanTaskConsumer(RedisConnectionFactory connectionFactory,
                                              SecurityScanner securityScanner,
                                              SecurityScanService securityScanService,
                                              SkillVersionRepository skillVersionRepository,
                                              SkillRepository skillRepository,
                                              ReviewTaskRepository reviewTaskRepository,
                                              ScanTaskProducer scanTaskProducer) {
        return new ScanTaskConsumer(
                connectionFactory,
                streamKey,
                groupName,
                securityScanner,
                securityScanService,
                skillVersionRepository,
                skillRepository,
                reviewTaskRepository,
                scanTaskProducer
        );
    }
}
