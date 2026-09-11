package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.suite.SkillSuite;
import com.iflytek.skillhub.domain.suite.SkillSuiteActionContext;
import com.iflytek.skillhub.domain.suite.SkillSuiteAuthorizationPolicy;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LabelPermissionChecker {

    public boolean canManageDefinitions(Set<String> platformRoles) {
        return platformRoles.contains("SUPER_ADMIN");
    }

    public boolean canManageSkillLabel(Skill skill,
                                       LabelDefinition labelDefinition,
                                       String userId,
                                       Map<Long, NamespaceRole> userNamespaceRoles,
                                       Set<String> platformRoles) {
        if (platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (labelDefinition.getType() == LabelType.PRIVILEGED) {
            return false;
        }
        NamespaceRole namespaceRole = userNamespaceRoles.get(skill.getNamespaceId());
        return userId.equals(skill.getOwnerId())
                || namespaceRole == NamespaceRole.ADMIN
                || namespaceRole == NamespaceRole.OWNER;
    }

    public boolean canManageSuiteLabel(
            SkillSuite suite,
            LabelDefinition labelDefinition,
            String userId,
            Map<Long, NamespaceRole> userNamespaceRoles,
            Set<String> platformRoles
    ) {
        if (platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }
        if (userId == null || labelDefinition.getType() == LabelType.PRIVILEGED) {
            return false;
        }
        return SkillSuiteAuthorizationPolicy.canCreateVersion(
                suite,
                new SkillSuiteActionContext(
                        userId, userNamespaceRoles, platformRoles, null, null, null));
    }
}
