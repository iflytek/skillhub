package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.media.MediaValidator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the {@link MediaValidator} with file-size limits sourced from
 * {@code skillhub.media.*}. Defaults are 10MB / 10MB matching the design doc.
 */
@Configuration
public class MediaConfig {

    @Bean
    @ConfigurationProperties(prefix = "skillhub.media")
    public MediaProperties mediaProperties() {
        return new MediaProperties();
    }

    @Bean
    public MediaValidator mediaValidator(MediaProperties properties) {
        return new MediaValidator(properties.getMaxGifSize(), properties.getMaxImageSize());
    }

    public static class MediaProperties {
        private long maxGifSize = 10L * 1024L * 1024L;
        private long maxImageSize = 10L * 1024L * 1024L;
        private long maxPromotionGifSize = 5L * 1024L * 1024L;

        public long getMaxGifSize() { return maxGifSize; }
        public void setMaxGifSize(long maxGifSize) { this.maxGifSize = maxGifSize; }

        public long getMaxImageSize() { return maxImageSize; }
        public void setMaxImageSize(long maxImageSize) { this.maxImageSize = maxImageSize; }

        public long getMaxPromotionGifSize() { return maxPromotionGifSize; }
        public void setMaxPromotionGifSize(long maxPromotionGifSize) { this.maxPromotionGifSize = maxPromotionGifSize; }
    }
}
