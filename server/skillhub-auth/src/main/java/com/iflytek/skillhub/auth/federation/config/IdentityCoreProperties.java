package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for {@code skillhub.identity.core.mode}; defaults to the legacy path. */
@ConfigurationProperties(prefix = "skillhub.identity.core")
public class IdentityCoreProperties {

    private static final Logger log = LoggerFactory.getLogger(IdentityCoreProperties.class);

    private IdentityCoreMode mode = IdentityCoreMode.LEGACY;

    @PostConstruct
    void logEffectiveMode() {
        log.info("Identity core mode: {}", mode);
    }

    public IdentityCoreMode getMode() {
        return mode;
    }

    public void setMode(IdentityCoreMode mode) {
        this.mode = Objects.requireNonNull(mode, "identity core mode must not be null");
    }
}
