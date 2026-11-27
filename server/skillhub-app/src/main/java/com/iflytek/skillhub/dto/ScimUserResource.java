package com.iflytek.skillhub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimUserResource(
        List<String> schemas,
        String id,
        String externalId,
        String userName,
        String displayName,
        Boolean active,
        ScimName name,
        List<ScimEmail> emails,
        @JsonProperty("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User")
        ScimEnterpriseUser enterprise,
        ScimMeta meta
) {
    public record ScimName(String givenName, String familyName) {}

    public record ScimEmail(String value, String type, Boolean primary) {}

    public record ScimEnterpriseUser(String department, String employeeNumber) {}

    public record ScimMeta(
            String resourceType,
            String created,
            String lastModified,
            String location,
            String version
    ) {}
}
