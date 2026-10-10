package com.iflytek.skillhub.domain.namespace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Deployment defaults for personal namespace provisioning.
 *
 * <p>Deployments can disable provisioning with the environment-backed {@code enabled} property.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.namespace.personal-provisioning")
public class PersonalNamespaceProvisioningProperties {

    /**
     * Off by default: existing deployments must not start creating namespaces after an upgrade.
     */
    private boolean enabled = true;

    /**
     * Templates remain code defaults because Spring treats {@code ${...}} in YAML as property
     * references.
     */
    private String slugTemplate = "personal-${random}";

    private String displayNameTemplate = "${username}-个人空间";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSlugTemplate() {
        return slugTemplate;
    }

    public void setSlugTemplate(String slugTemplate) {
        this.slugTemplate = slugTemplate;
    }

    public String getDisplayNameTemplate() {
        return displayNameTemplate;
    }

    public void setDisplayNameTemplate(String displayNameTemplate) {
        this.displayNameTemplate = displayNameTemplate;
    }

    public PersonalNamespaceSettings toSettings() {
        return new PersonalNamespaceSettings(enabled, slugTemplate, displayNameTemplate);
    }
}
