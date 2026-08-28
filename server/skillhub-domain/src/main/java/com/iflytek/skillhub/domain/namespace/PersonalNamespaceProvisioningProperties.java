package com.iflytek.skillhub.domain.namespace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Deployment defaults for personal namespace provisioning.
 *
 * <p>These apply until an administrator saves the setting in the admin console, after which the
 * stored value wins. Deployments that manage configuration purely through files can therefore keep
 * doing so and never touch the console.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.namespace.personal-provisioning")
public class PersonalNamespaceProvisioningProperties {

    /**
     * Off by default: existing deployments must not start creating namespaces after an upgrade.
     */
    private boolean enabled = false;

    /**
     * Kept out of {@code application.yml}: the {@code ${...}} placeholders would be resolved as
     * Spring property references. Operators change the templates in the admin console, so these
     * defaults only apply until someone does.
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
