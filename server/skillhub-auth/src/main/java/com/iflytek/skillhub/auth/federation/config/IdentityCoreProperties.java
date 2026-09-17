package com.iflytek.skillhub.auth.federation.config;

import com.iflytek.skillhub.auth.federation.core.IdentityCoreMode;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for {@code skillhub.identity.core.mode}; defaults to the legacy path. */
@ConfigurationProperties(prefix = "skillhub.identity.core")
public class IdentityCoreProperties {

    private IdentityCoreMode mode = IdentityCoreMode.LEGACY;

    public IdentityCoreMode getMode() {
        return mode;
    }

    public void setMode(IdentityCoreMode mode) {
        this.mode = Objects.requireNonNull(mode, "identity core mode must not be null");
    }
}
