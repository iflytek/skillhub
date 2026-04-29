package com.iflytek.skillhub.auth.uass.config;

import com.iflytek.skillhub.auth.uass.UassProperties;
import com.iflytek.skillhub.auth.uass.store.LocalUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.RedisUassLoginStateStore;
import com.iflytek.skillhub.auth.uass.store.UassLoginStateStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class UassStateStoreConfiguration {

    @Bean
    public UassLoginStateStore uassLoginStateStore(
            UassProperties properties,
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        return switch (properties.getCacheMode()) {
            case LOCAL -> new LocalUassLoginStateStore(properties.getStateTtl());
            case REDIS -> {
                if (redisTemplate == null) {
                    throw new IllegalStateException("UASS cacheMode=redis requires RedisTemplate<String, Object>");
                }
                yield new RedisUassLoginStateStore(redisTemplate, properties.getStateTtl());
            }
            case AUTO -> redisTemplate != null
                    ? new RedisUassLoginStateStore(redisTemplate, properties.getStateTtl())
                    : new LocalUassLoginStateStore(properties.getStateTtl());
        };
    }
}
