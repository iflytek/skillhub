package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.compat.dto.ClawHubRegistrySearchResponse;
import com.iflytek.skillhub.compat.dto.ClawHubRegistrySkillResponse;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ClawHubRegistryController {

    private final ClawHubRegistryFacade facade;

    public ClawHubRegistryController(ClawHubRegistryFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/search")
    public ClawHubRegistrySearchResponse search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return facade.search(q, limit, userId, userNsRoles);
    }

    @GetMapping("/skills/{slug}")
    public ClawHubRegistrySkillResponse getSkill(
            @org.springframework.web.bind.annotation.PathVariable String slug,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return facade.getSkill(slug, userId, userNsRoles);
    }

    @GetMapping("/download")
    public ResponseEntity<Void> download(
            @RequestParam String slug,
            @RequestParam(required = false) String version,
            @RequestAttribute(value = "userId", required = false) String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        String location = facade.resolveDownloadUrl(slug, version, userId, userNsRoles);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
