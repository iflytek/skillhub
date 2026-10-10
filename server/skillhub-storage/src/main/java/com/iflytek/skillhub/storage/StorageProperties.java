package com.iflytek.skillhub.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.storage")
public class StorageProperties {
    private String provider = "local";
    private LocalProperties local = new LocalProperties();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public LocalProperties getLocal() { return local; }
    public void setLocal(LocalProperties local) { this.local = local; }

    public static class LocalProperties {
        private String basePath = "./data/storage";
        public String getBasePath() { return basePath; }
        public void setBasePath(String basePath) { this.basePath = basePath; }
    }
}
