package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.time.Duration;

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
}
