package com.iflytek.skillhub.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "skillhub.security.scanner", name = "enabled", havingValue = "true")
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        return Redisson.create(createConfig(redisProperties));
    }

    static Config createConfig(RedisProperties redisProperties) {
        Config config = new Config();
        if (hasSentinelConfiguration(redisProperties)) {
            configureSentinelServers(config, redisProperties);
            return config;
        }

        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(resolveAddress(redisProperties))
                .setDatabase(redisProperties.getDatabase());

        applySharedSettings(singleServerConfig, redisProperties);

        return config;
    }

    private static void configureSentinelServers(Config config, RedisProperties redisProperties) {
        SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                .setMasterName(redisProperties.getSentinel().getMaster())
                .setDatabase(redisProperties.getDatabase());
        List<String> nodes = redisProperties.getSentinel().getNodes();
        nodes.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(node -> withRedisScheme(node, redisProperties))
                .forEach(sentinelServersConfig::addSentinelAddress);

        applySharedSettings(sentinelServersConfig, redisProperties);
    }

    private static boolean hasSentinelConfiguration(RedisProperties redisProperties) {
        return redisProperties.getSentinel() != null
                && StringUtils.hasText(redisProperties.getSentinel().getMaster())
                && redisProperties.getSentinel().getNodes() != null
                && !redisProperties.getSentinel().getNodes().isEmpty();
    }

    private static void applySharedSettings(org.redisson.config.BaseConfig<?> serverConfig,
                                            RedisProperties redisProperties) {
        if (StringUtils.hasText(redisProperties.getUsername())) {
            serverConfig.setUsername(redisProperties.getUsername());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            serverConfig.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasText(redisProperties.getClientName())) {
            serverConfig.setClientName(redisProperties.getClientName());
        }
        if (redisProperties.getTimeout() != null) {
            serverConfig.setTimeout(Math.toIntExact(redisProperties.getTimeout().toMillis()));
        }
        if (redisProperties.getConnectTimeout() != null) {
            serverConfig.setConnectTimeout(Math.toIntExact(redisProperties.getConnectTimeout().toMillis()));
        }
    }

    private static String resolveAddress(RedisProperties redisProperties) {
        if (StringUtils.hasText(redisProperties.getUrl())) {
            return redisProperties.getUrl();
        }
        return withRedisScheme(redisProperties.getHost() + ":" + redisProperties.getPort(), redisProperties);
    }

    private static String withRedisScheme(String address, RedisProperties redisProperties) {
        if (address.startsWith("redis://") || address.startsWith("rediss://")) {
            return address;
        }
        String scheme = redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled()
                ? "rediss"
                : "redis";
        return scheme + "://" + address;
    }
}
