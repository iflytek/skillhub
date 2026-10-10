package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class));

    @Test
    void autoConfiguration_keepsStandaloneAsTheDefault() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.host=redis.internal",
                        "spring.data.redis.port=6380",
                        "spring.data.redis.database=4")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    RedisStandaloneConfiguration standalone = factory.getStandaloneConfiguration();

                    assertThat(factory.getClusterConfiguration()).isNull();
                    assertThat(factory.getSentinelConfiguration()).isNull();
                    assertThat(standalone).isNotNull();
                    assertThat(standalone.getHostName()).isEqualTo("redis.internal");
                    assertThat(standalone.getPort()).isEqualTo(6380);
                    assertThat(standalone.getDatabase()).isEqualTo(4);
                });
    }

    @Test
    void autoConfiguration_selectsClusterFromStandardSpringProperties() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.cluster.nodes=redis-1:6379,redis-2:6380",
                        "spring.data.redis.cluster.max-redirects=5",
                        "spring.data.redis.username=skillhub",
                        "spring.data.redis.password=secret")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    RedisClusterConfiguration cluster = factory.getClusterConfiguration();

                    assertThat(factory.isClusterAware()).isTrue();
                    assertThat(factory.getSentinelConfiguration()).isNull();
                    assertThat(cluster).isNotNull();
                    assertThat(cluster.getClusterNodes())
                            .extracting(node -> node.getHost() + ":" + node.getPort())
                            .containsExactly("redis-1:6379", "redis-2:6380");
                    assertThat(cluster.getMaxRedirects()).isEqualTo(5);
                    assertThat(cluster.getUsername()).isEqualTo("skillhub");
                    assertThat(cluster.getPassword().map(String::new).orElse(null)).isEqualTo("secret");
                });
    }

    @Test
    void autoConfiguration_prefersSentinelWhenSentinelAndClusterAreBothConfigured() {
        contextRunner
                .withPropertyValues(
                        "spring.data.redis.sentinel.master=mymaster",
                        "spring.data.redis.sentinel.nodes=sentinel-1:26379,sentinel-2:26379",
                        "spring.data.redis.cluster.nodes=redis-1:6379,redis-2:6380")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);
                    RedisSentinelConfiguration sentinel = factory.getSentinelConfiguration();

                    assertThat(factory.isRedisSentinelAware()).isTrue();
                    assertThat(factory.getClusterConfiguration()).isNull();
                    assertThat(sentinel).isNotNull();
                    assertThat(sentinel.getMaster().getName()).isEqualTo("mymaster");
                    assertThat(sentinel.getSentinels())
                            .extracting(node -> node.getHost() + ":" + node.getPort())
                            .containsExactlyInAnyOrder("sentinel-1:26379", "sentinel-2:26379");
                });
    }
}
