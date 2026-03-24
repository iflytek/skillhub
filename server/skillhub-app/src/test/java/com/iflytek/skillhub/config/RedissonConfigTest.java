package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedissonConfigTest {

    @Test
    void createConfig_buildsRedisAddressFromHostPortAndSslFlag() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("redis.internal");
        properties.setPort(6380);
        properties.getSsl().setEnabled(true);

        Config config = RedissonConfig.createConfig(properties);
        SingleServerConfig serverConfig = config.useSingleServer();

        assertThat(serverConfig.getAddress()).isEqualTo("rediss://redis.internal:6380");
    }

    @Test
    void createConfig_prefersExplicitRedisUrlWhenPresent() {
        RedisProperties properties = new RedisProperties();
        properties.setUrl("redis://cache.example:6379");
        properties.setHost("ignored-host");
        properties.setPort(6380);

        Config config = RedissonConfig.createConfig(properties);
        SingleServerConfig serverConfig = config.useSingleServer();

        assertThat(serverConfig.getAddress()).isEqualTo("redis://cache.example:6379");
    }

    @Test
    void createConfig_appliesDatabaseCredentialsClientNameAndTimeouts() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("localhost");
        properties.setPort(6379);
        properties.setDatabase(5);
        properties.setUsername("skillhub");
        properties.setPassword("secret");
        properties.setClientName("skillhub-stream");
        properties.setTimeout(Duration.ofSeconds(3));
        properties.setConnectTimeout(Duration.ofSeconds(5));

        Config config = RedissonConfig.createConfig(properties);
        SingleServerConfig serverConfig = config.useSingleServer();

        assertThat(serverConfig.getDatabase()).isEqualTo(5);
        assertThat(serverConfig.getUsername()).isEqualTo("skillhub");
        assertThat(serverConfig.getPassword()).isEqualTo("secret");
        assertThat(serverConfig.getClientName()).isEqualTo("skillhub-stream");
        assertThat(serverConfig.getTimeout()).isEqualTo(3000);
        assertThat(serverConfig.getConnectTimeout()).isEqualTo(5000);
    }

    @Test
    void createConfig_usesSentinelServersWhenSentinelPropertiesArePresent() throws Exception {
        RedisProperties properties = new RedisProperties();
        RedisProperties.Sentinel sentinel = new RedisProperties.Sentinel();
        sentinel.setMaster("mymaster");
        sentinel.setNodes(List.of("redis-sentinel-1:26379", "redis-sentinel-2:26379"));
        properties.setSentinel(sentinel);
        properties.setDatabase(3);
        properties.setPassword("secret");
        properties.setClientName("skillhub-stream");
        properties.setTimeout(Duration.ofSeconds(3));
        properties.setConnectTimeout(Duration.ofSeconds(5));

        Config config = RedissonConfig.createConfig(properties);
        SentinelServersConfig sentinelConfig = sentinelConfig(config);

        assertThat(config.isSentinelConfig()).isTrue();
        assertThat(sentinelConfig.getMasterName()).isEqualTo("mymaster");
        assertThat(sentinelConfig.getSentinelAddresses())
                .containsExactly("redis://redis-sentinel-1:26379", "redis://redis-sentinel-2:26379");
        assertThat(sentinelConfig.getDatabase()).isEqualTo(3);
        assertThat(sentinelConfig.getPassword()).isEqualTo("secret");
        assertThat(sentinelConfig.getClientName()).isEqualTo("skillhub-stream");
        assertThat(sentinelConfig.getTimeout()).isEqualTo(3000);
        assertThat(sentinelConfig.getConnectTimeout()).isEqualTo(5000);
    }

    @Test
    void createConfig_usesSecureSchemeForSentinelAddressesWhenSslEnabled() throws Exception {
        RedisProperties properties = new RedisProperties();
        RedisProperties.Sentinel sentinel = new RedisProperties.Sentinel();
        sentinel.setMaster("mymaster");
        sentinel.setNodes(List.of("redis-sentinel-1:26379"));
        properties.setSentinel(sentinel);
        properties.getSsl().setEnabled(true);

        Config config = RedissonConfig.createConfig(properties);
        SentinelServersConfig sentinelConfig = sentinelConfig(config);

        assertThat(sentinelConfig.getSentinelAddresses()).containsExactly("rediss://redis-sentinel-1:26379");
    }

    private SentinelServersConfig sentinelConfig(Config config) throws Exception {
        Method method = Config.class.getDeclaredMethod("getSentinelServersConfig");
        method.setAccessible(true);
        return (SentinelServersConfig) method.invoke(config);
    }
}
