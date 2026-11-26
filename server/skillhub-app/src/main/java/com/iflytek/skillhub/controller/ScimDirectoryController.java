package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.iflytek.skillhub.config.DirectoryProvisioningProperties;
import com.iflytek.skillhub.dto.ScimListResponse;
import com.iflytek.skillhub.dto.ScimUserRequest;
import com.iflytek.skillhub.dto.ScimUserResource;
import com.iflytek.skillhub.service.DirectoryScimAppService;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/organizations/{organizationId}/directory-connections/{connectionId}/scim/v2",
        produces = "application/scim+json"
)
public class ScimDirectoryController {

    private static final MediaType SCIM_JSON = MediaType.parseMediaType("application/scim+json");

    private final DirectoryScimAppService scim;
    private final DirectoryProvisioningProperties properties;

    public ScimDirectoryController(
            DirectoryScimAppService scim,
            DirectoryProvisioningProperties properties
    ) {
        this.scim = scim;
        this.properties = properties;
    }

    @GetMapping("/ServiceProviderConfig")
    public ResponseEntity<Map<String, Object>> serviceProviderConfig() {
        return ResponseEntity.ok().contentType(SCIM_JSON).body(Map.of(
                "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
                "patch", Map.of("supported", true),
                "filter", Map.of("supported", true, "maxResults", properties.getMaxPageCount()),
                "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false),
                "etag", Map.of("supported", true),
                "authenticationSchemes", List.of(Map.of(
                        "name", "OAuth Bearer Token",
                        "description", "Organization-scoped bearer token",
                        "specUri", "https://datatracker.ietf.org/doc/html/rfc6750",
                        "type", "oauthbearertoken"
                ))
        ));
    }

    @GetMapping("/ResourceTypes")
    public ResponseEntity<List<Map<String, Object>>> resourceTypes() {
        return ResponseEntity.ok().contentType(SCIM_JSON).body(List.of(Map.of(
                "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                "id", "User",
                "name", "User",
                "endpoint", "/Users",
                "schema", "urn:ietf:params:scim:schemas:core:2.0:User"
        )));
    }

    @GetMapping("/Schemas")
    public ResponseEntity<List<Map<String, Object>>> schemas() {
        return ResponseEntity.ok().contentType(SCIM_JSON).body(List.of(
                Map.of(
                        "id", "urn:ietf:params:scim:schemas:core:2.0:User",
                        "name", "User",
                        "description", "SkillHub provisioned user"
                ),
                Map.of(
                        "id", "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User",
                        "name", "EnterpriseUser",
                        "description", "SkillHub directory profile extension"
                )
        ));
    }

    @GetMapping("/Users")
    public ResponseEntity<ScimListResponse> listUsers(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count,
            @RequestParam(required = false) String filter
    ) {
        return ResponseEntity.ok().contentType(SCIM_JSON)
                .body(scim.list(organizationId, connectionId, startIndex, count, filter));
    }

    @GetMapping("/Users/{membershipId}")
    public ResponseEntity<ScimUserResource> getUser(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @PathVariable String membershipId
    ) {
        ScimUserResource resource = scim.get(organizationId, connectionId, membershipId);
        return ResponseEntity.ok().contentType(SCIM_JSON)
                .eTag(resource.meta().version())
                .body(resource);
    }

    @PostMapping(value = "/Users", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ScimUserResource> createUser(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @RequestBody ScimUserRequest request,
            HttpServletRequest httpRequest
    ) {
        scim.validateRequestSize(httpRequest.getContentLengthLong());
        ScimUserResource resource = scim.create(organizationId, connectionId, request);
        return ResponseEntity.status(201).contentType(SCIM_JSON)
                .eTag(resource.meta().version())
                .body(resource);
    }

    @PutMapping(value = "/Users/{membershipId}", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ScimUserResource> replaceUser(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @PathVariable String membershipId,
            @RequestBody ScimUserRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest httpRequest
    ) {
        scim.validateRequestSize(httpRequest.getContentLengthLong());
        ScimUserResource resource = scim.replace(organizationId, connectionId, membershipId, request, ifMatch);
        return ResponseEntity.ok().contentType(SCIM_JSON)
                .eTag(resource.meta().version())
                .body(resource);
    }

    @PatchMapping(value = "/Users/{membershipId}", consumes = {"application/scim+json", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ScimUserResource> patchUser(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @PathVariable String membershipId,
            @RequestBody JsonNode request,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest httpRequest
    ) {
        scim.validateRequestSize(httpRequest.getContentLengthLong());
        ScimUserResource resource = scim.patch(organizationId, connectionId, membershipId, request, ifMatch);
        return ResponseEntity.ok().contentType(SCIM_JSON)
                .eTag(resource.meta().version())
                .body(resource);
    }

    @DeleteMapping("/Users/{membershipId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String organizationId,
            @PathVariable String connectionId,
            @PathVariable String membershipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch
    ) {
        scim.delete(organizationId, connectionId, membershipId, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
