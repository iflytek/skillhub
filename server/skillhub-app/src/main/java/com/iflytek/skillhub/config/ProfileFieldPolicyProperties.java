package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.user.ProfileFieldPolicyConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds {@code skillhub.profile.fields} from application.yml to per-field policies.
 */
@ConfigurationProperties(prefix = "skillhub.profile")
public class ProfileFieldPolicyProperties implements ProfileFieldPolicyConfig {

    private Map<String, FieldEntry> fields = new LinkedHashMap<>();

    public Map<String, FieldEntry> getFields() {
        return fields;
    }

    public void setFields(Map<String, FieldEntry> fields) {
        this.fields = fields;
    }

    @Override
    public Map<String, FieldPolicy> fieldPolicies() {
        Map<String, FieldPolicy> result = new LinkedHashMap<>();
        fields.forEach((name, entry) ->
                result.put(name, new FieldPolicy(entry.isEditable(), entry.isRequiresReview())));
        return result;
    }

    public static class FieldEntry {
        private boolean editable;
        private boolean requiresReview;

        public boolean isEditable() { return editable; }
        public void setEditable(boolean editable) { this.editable = editable; }
        public boolean isRequiresReview() { return requiresReview; }
        public void setRequiresReview(boolean requiresReview) { this.requiresReview = requiresReview; }
    }
}
