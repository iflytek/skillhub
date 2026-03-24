package com.iflytek.skillhub.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        return Redisson.create(createConfig(redisProperties));
    }

    static Config createConfig(RedisProperties redisProperties) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(resolveAddress(redisProperties))
                .setDatabase(redisProperties.getDatabase());

        if (StringUtils.hasText(redisProperties.getUsername())) {
            singleServerConfig.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasText(redisProperties.getClientName())) {
            singleServerConfig.setClientName(redisProperties.getClientName());
        }
        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout(Math.toIntExact(redisProperties.getTimeout().toMillis()));
        }
        if (redisProperties.getConnectTimeout() != null) {
            singleServerConfig.setConnectTimeout(Math.toIntExact(redisProperties.getConnectTimeout().toMillis()));
        }

        return config;
    }

    private static String resolveAddress(RedisProperties redisProperties) {
        if (StringUtils.hasText(redisProperties.getUrl())) {
            return redisProperties.getUrl();
        }
        String scheme = redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled()
                ? "rediss"
                : "redis";
        return scheme + "://" + redisProperties.getHost() + ":" + redisProperties.getPort();
    }
}
