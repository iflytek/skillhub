package com.iflytek.skillhub.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skillhub.auth.sso")
public class SsoProperties {

    /** Kept disabled in OSS by default. */
    private boolean enabled = false;

    /** SSO server base URL. */
    private String baseUrl;

    /** Ticket validation endpoint path on SSO server. */
    private String validatePath;

    /** Client URL registered in SSO (used as callback base). */
    private String clientUrl;

    /** Client token registered in SSO, used for logout API calls. */
    private String clientToken;

    /** Response field mapping for SSO user info JSON. */
    private ResponseFields response = new ResponseFields();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getValidatePath() { return validatePath; }
    public void setValidatePath(String validatePath) { this.validatePath = validatePath; }

    public String getClientUrl() { return clientUrl; }
    public void setClientUrl(String clientUrl) { this.clientUrl = clientUrl; }

    public String getClientToken() { return clientToken; }
    public void setClientToken(String clientToken) { this.clientToken = clientToken; }

    public ResponseFields getResponse() { return response; }
    public void setResponse(ResponseFields response) { this.response = response; }

    public static class ResponseFields {
        private String accountField = "account";
        private String idField = "id";
        private String nameField = "name";

        public String getAccountField() { return accountField; }
        public void setAccountField(String accountField) { this.accountField = accountField; }

        public String getIdField() { return idField; }
        public void setIdField(String idField) { this.idField = idField; }

        public String getNameField() { return nameField; }
        public void setNameField(String nameField) { this.nameField = nameField; }
    }
}
