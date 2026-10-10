package com.iflytek.skillhub.domain.suite;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;

import java.util.Map;
import java.util.Set;

/** Caller authorization and request metadata shared by Suite lifecycle operations. */
public record SkillSuiteActionContext(
        String actorUserId,
        Map<Long, NamespaceRole> namespaceRoles,
        Set<String> platformRoles,
        String requestId,
        String clientIp,
        String userAgent
) {
    public SkillSuiteActionContext {
        namespaceRoles = namespaceRoles == null ? Map.of() : Map.copyOf(namespaceRoles);
        platformRoles = platformRoles == null ? Set.of() : Set.copyOf(platformRoles);
    }
}
