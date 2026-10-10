package com.iflytek.skillhub.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.redisson.Redisson;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.redisson.api.stream.StreamAddArgs.entry;

@Tag("redis-cluster")
@EnabledIfEnvironmentVariable(named = "REDIS_CLUSTER_TEST_NODES", matches = ".+")
class RedisClusterIntegrationTest {

    @Test
    void clusterSupportsSpringDataSessionsAndRedissonStreams() {
        List<String> nodes = clusterNodes();
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(nodes);
        String username = System.getenv("REDIS_CLUSTER_TEST_USERNAME");
        String password = System.getenv("REDIS_CLUSTER_TEST_PASSWORD");
        if (username != null && !username.isBlank()) {
            clusterConfiguration.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            clusterConfiguration.setPassword(password);
        }

        boolean sslEnabled = Boolean.parseBoolean(System.getenv("REDIS_CLUSTER_TEST_SSL_ENABLED"));
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfiguration =
                LettuceClientConfiguration.builder();
        if (sslEnabled) {
            clientConfiguration.useSsl();
        }

        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(clusterConfiguration, clientConfiguration.build());
        connectionFactory.afterPropertiesSet();

        RedisProperties properties = new RedisProperties();
        RedisProperties.Cluster cluster = new RedisProperties.Cluster();
        cluster.setNodes(nodes);
        properties.setCluster(cluster);
        properties.setUsername(username);
        properties.setPassword(password);
        properties.getSsl().setEnabled(sslEnabled);

        Config redissonConfig = RedissonConfig.createConfig(properties);
        String keyPrefix = "skillhub:redis-cluster-smoke:" + UUID.randomUUID();
        RedissonClient redissonClient = Redisson.create(redissonConfig);
        try {
            verifySpringData(connectionFactory, keyPrefix);
            verifySpringSession(connectionFactory, keyPrefix);
            verifyRedissonStream(redissonClient, keyPrefix);
        } finally {
            redissonClient.shutdown();
            connectionFactory.destroy();
        }
    }

    private void verifySpringData(LettuceConnectionFactory connectionFactory, String keyPrefix) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        String key = keyPrefix + ":value";

        template.opsForValue().set(key, "ok");

        assertThat(template.opsForValue().get(key)).isEqualTo("ok");
        template.delete(key);
    }

    private void verifySpringSession(LettuceConnectionFactory connectionFactory, String keyPrefix) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.afterPropertiesSet();

        RedisIndexedSessionRepository repository = new RedisIndexedSessionRepository(template);
        repository.setRedisKeyNamespace(keyPrefix + ":session");
        repository.afterPropertiesSet();
        try {
            SessionRepository<Session> sessionRepository = sessionRepository(repository);
            Session session = sessionRepository.createSession();
            session.setAttribute("userId", "cluster-user");
            sessionRepository.save(session);

            Session loaded = sessionRepository.findById(session.getId());
            assertThat(loaded).isNotNull();
            assertThat(loaded.<String>getAttribute("userId")).isEqualTo("cluster-user");

            sessionRepository.deleteById(session.getId());
            assertThat(sessionRepository.findById(session.getId())).isNull();
        } finally {
            repository.destroy();
        }
    }

    private void verifyRedissonStream(RedissonClient redissonClient, String keyPrefix) {
        RStream<String, String> stream = redissonClient.getStream(keyPrefix + ":stream", StringCodec.INSTANCE);
        try {
            stream.add(entry("status", "ok"));
            assertThat(stream.size()).isEqualTo(1);
        } finally {
            stream.delete();
        }
    }

    private List<String> clusterNodes() {
        return Arrays.stream(System.getenv("REDIS_CLUSTER_TEST_NODES").split(","))
                .map(String::trim)
                .filter(node -> !node.isEmpty())
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private SessionRepository<Session> sessionRepository(RedisIndexedSessionRepository repository) {
        return (SessionRepository) repository;
    }
}
