package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScimUserRequest(
        List<String> schemas,
        String externalId,
        String userName,
        String displayName,
        Boolean active,
        ScimUserResource.ScimName name,
        List<ScimUserResource.ScimEmail> emails,
        @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
        ScimUserResource.ScimEnterpriseUser enterprise
) {}
