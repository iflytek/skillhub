package com.iflytek.skillhub.domain.authoring.service;

import com.iflytek.skillhub.domain.authoring.*;
import com.iflytek.skillhub.domain.shared.exception.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RuntimeBindingEnvironmentAuthorizationTest {
    @Test void ordinaryDraftOwnerCanSaveOnlyDeploymentApprovedSelection() {
        var bindings = mock(RuntimeBindingRepository.class);
        var drafts = mock(SkillDraftRepository.class);
        var policy = new ScriptEnvironmentPolicy(() -> List.of("AUTHORING_DEMO"));
        var service = new RuntimeBindingService(bindings, drafts, new RuntimeBindingValidator(policy));
        when(drafts.findById(7L)).thenReturn(Optional.of(new SkillDraft(1L, "ordinary-member", "test", "test", "digest")));
        when(bindings.findByDraftId(7L)).thenReturn(Optional.empty());
        when(bindings.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.saveBinding(7L, "ordinary-member", "local-script",
                Map.of("envAllowlist", List.of("AUTHORING_DEMO")), null, null, Set.of());
        assertThat(result.getConfig()).containsKey("envAllowlist");
        verify(bindings).save(any());
        clearInvocations(bindings);
        assertThatThrownBy(() -> service.saveBinding(7L, "ordinary-member", "local-script",
                Map.of("envAllowlist", List.of("AUTHORING_OTHER")), null, null, Set.of()))
                .isInstanceOf(DomainBadRequestException.class);
        verify(bindings, never()).save(any());
    }
    @Test void superAdminRoleDoesNotExpandDeploymentGrant() {
        var bindings = mock(RuntimeBindingRepository.class);
        var drafts = mock(SkillDraftRepository.class);
        when(drafts.findById(7L)).thenReturn(Optional.of(new SkillDraft(1L, "ordinary-member", "test", "test", "digest")));
        var service = new RuntimeBindingService(bindings, drafts,
                new RuntimeBindingValidator(new ScriptEnvironmentPolicy(List::of)));
        assertThatThrownBy(() -> service.saveBinding(7L, "admin", "local-script",
                Map.of("envAllowlist", List.of("AUTHORING_DEMO")), null, null, Set.of("SUPER_ADMIN")))
                .isInstanceOf(DomainBadRequestException.class);
        verify(bindings, never()).save(any());
    }
    @Test void nonOwnerCannotUseAnOtherwiseAuthorizedSelection() {
        var bindings = mock(RuntimeBindingRepository.class);
        var drafts = mock(SkillDraftRepository.class);
        when(drafts.findById(7L)).thenReturn(Optional.of(new SkillDraft(1L, "owner", "test", "test", "digest")));
        var service = new RuntimeBindingService(bindings, drafts,
                new RuntimeBindingValidator(new ScriptEnvironmentPolicy(() -> List.of("AUTHORING_DEMO"))));
        assertThatThrownBy(() -> service.saveBinding(7L, "other-member", "local-script",
                Map.of("envAllowlist", List.of("AUTHORING_DEMO")), null, null, Set.of()))
                .isInstanceOf(DomainForbiddenException.class);
        verify(bindings, never()).save(any());
    }
}
