package com.iflytek.skillhub.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(
            RedisProperties redisProperties,
            @Value("${skillhub.redis.sentinel.check-sentinels-list:true}") boolean checkSentinelsList) {
        return Redisson.create(createConfig(redisProperties, checkSentinelsList));
    }

    static Config createConfig(RedisProperties redisProperties) {
        return createConfig(redisProperties, true);
    }

    static Config createConfig(RedisProperties redisProperties, boolean checkSentinelsList) {
        Config config = new Config();
        if (hasSentinelConfiguration(redisProperties)) {
            configureSentinelServers(config, redisProperties, checkSentinelsList);
            return config;
        }
        if (hasClusterConfiguration(redisProperties)) {
            configureClusterServers(config, redisProperties);
            return config;
        }

        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(resolveAddress(redisProperties))
                .setDatabase(redisProperties.getDatabase());

        applySharedSettings(singleServerConfig, redisProperties);

        return config;
    }

    private static void configureSentinelServers(
            Config config,
            RedisProperties redisProperties,
            boolean checkSentinelsList) {
        SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                .setMasterName(redisProperties.getSentinel().getMaster())
                .setDatabase(redisProperties.getDatabase())
                .setCheckSentinelsList(checkSentinelsList);
        List<String> nodes = redisProperties.getSentinel().getNodes();
        nodes.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(node -> withRedisScheme(node, redisProperties))
                .forEach(sentinelServersConfig::addSentinelAddress);

        applySharedSettings(sentinelServersConfig, redisProperties);
        if (StringUtils.hasText(redisProperties.getSentinel().getPassword())) {
            sentinelServersConfig.setSentinelPassword(redisProperties.getSentinel().getPassword());
        }
        if (StringUtils.hasText(redisProperties.getSentinel().getUsername())) {
            sentinelServersConfig.setSentinelUsername(redisProperties.getSentinel().getUsername());
        }
    }

    private static void configureClusterServers(Config config, RedisProperties redisProperties) {
        ClusterServersConfig clusterServersConfig = config.useClusterServers();
        redisProperties.getCluster().getNodes().stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(node -> withRedisScheme(node, redisProperties))
                .forEach(clusterServersConfig::addNodeAddress);

        applySharedSettings(clusterServersConfig, redisProperties);
    }

    private static boolean hasSentinelConfiguration(RedisProperties redisProperties) {
        return redisProperties.getSentinel() != null
                && StringUtils.hasText(redisProperties.getSentinel().getMaster())
                && redisProperties.getSentinel().getNodes() != null
                && !redisProperties.getSentinel().getNodes().isEmpty();
    }

    private static boolean hasClusterConfiguration(RedisProperties redisProperties) {
        return redisProperties.getCluster() != null
                && redisProperties.getCluster().getNodes() != null
                && redisProperties.getCluster().getNodes().stream().anyMatch(StringUtils::hasText);
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
