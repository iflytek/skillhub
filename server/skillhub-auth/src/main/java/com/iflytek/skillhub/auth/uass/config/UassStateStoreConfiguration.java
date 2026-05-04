package com.iflytek.skillhub.auth.uass.config;

import com.iflytek.skillhub.auth.uass.UassProperties;
import com.iflytek.skillhub.auth.uass.store.LocalUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.RedisUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
public class UassStateStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(UassStateStoreConfiguration.class);

    @Bean
    public UassLoginStateStore uassLoginStateStore(
            UassProperties properties,
            @Qualifier("skillhubRedisTemplate")
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        if (!properties.isEnabled()) {
            return new LocalUassLoginStateStore(properties.getStateTtl());
        }

        return switch (properties.getCacheMode()) {
            case LOCAL -> localStore(properties, "cacheMode=local");
            case REDIS -> {
                RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
                yield redisStore(properties, requireAvailableRedis(redisTemplate, "cacheMode=redis"));
            }
            case AUTO -> {
                RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
                if (isRedisAvailable(redisTemplate)) {
                    yield redisStore(properties, redisTemplate);
                }
                String reason = redisTemplate == null
                        ? "cacheMode=auto with no RedisTemplate available"
                        : "cacheMode=auto because Redis is unavailable";
                yield localStore(properties, reason);
            }
        };
    }

    private static UassLoginStateStore localStore(UassProperties properties, String reason) {
        log.warn("UASS login state store is running in LOCAL mode ({}). Login state is not shared across replicas.",
                reason);
        return new LocalUassLoginStateStore(properties.getStateTtl());
    }

    private static UassLoginStateStore redisStore(
            UassProperties properties,
            RedisTemplate<String, Object> redisTemplate) {
        log.info("UASS login state store is running in REDIS mode. Login state is shared across replicas.");
        return new RedisUassLoginStateStore(redisTemplate, properties.getStateTtl());
    }

    private static RedisTemplate<String, Object> requireAvailableRedis(
            RedisTemplate<String, Object> redisTemplate,
            String reason) {
        if (!isRedisAvailable(redisTemplate)) {
            throw new IllegalStateException("UASS " + reason + " requires an available Redis connection");
        }
        return redisTemplate;
    }

    private static boolean isRedisAvailable(RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate == null) {
            return false;
        }
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return false;
        }
        try (var connection = connectionFactory.getConnection()) {
            return connection != null && StringUtils.hasText(connection.ping());
        } catch (Exception exception) {
            return false;
        }
    }
}
