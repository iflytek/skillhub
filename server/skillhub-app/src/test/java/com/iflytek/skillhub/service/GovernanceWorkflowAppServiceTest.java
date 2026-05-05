package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.NamespaceLifecycleRequest;
import com.iflytek.skillhub.dto.NamespaceResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GovernanceWorkflowAppServiceTest {

    private final ReviewPortalAppService reviewPortalAppService = mock(ReviewPortalAppService.class);
    private final ReviewSkillDetailAppService reviewSkillDetailAppService = mock(ReviewSkillDetailAppService.class);
    private final PromotionPortalAppService promotionPortalAppService = mock(PromotionPortalAppService.class);
    private final SkillLifecycleAppService skillLifecycleAppService = mock(SkillLifecycleAppService.class);
    private final NamespacePortalCommandAppService namespacePortalCommandAppService = mock(NamespacePortalCommandAppService.class);

    private final GovernanceWorkflowAppService service = new GovernanceWorkflowAppService(
            reviewPortalAppService,
            reviewSkillDetailAppService,
            promotionPortalAppService,
            skillLifecycleAppService,
            namespacePortalCommandAppService
    );

    @Test
    void freezeNamespace_delegatesToNamespaceCommandService() {
        NamespaceResponse expected = new NamespaceResponse(1L, "global", "Global", NamespaceStatus.ACTIVE, null, null, null, null, null, null);
        when(namespacePortalCommandAppService.freezeNamespace("global", new NamespaceLifecycleRequest("maintenance"), "admin-1", new AuditRequestContext("127.0.0.1", "JUnit")))
                .thenReturn(expected);

        NamespaceResponse result = service.freezeNamespace("global", new NamespaceLifecycleRequest("maintenance"), "admin-1", new AuditRequestContext("127.0.0.1", "JUnit"));

        assertThat(result).isEqualTo(expected);
    }
}
