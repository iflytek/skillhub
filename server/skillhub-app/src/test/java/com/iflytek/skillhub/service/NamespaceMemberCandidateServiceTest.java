package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceAccessPolicy;
import com.iflytek.skillhub.domain.namespace.NamespaceService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.dto.NamespaceCandidateUserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceMemberCandidateServiceTest {

    @Mock
    private NamespaceService namespaceService;

    @Mock
    private NamespaceAccessPolicy namespaceAccessPolicy;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Test
    void searchCandidates_shouldFilterExistingMembers() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        setField(namespace, "id", 1L);

        NamespaceMemberCandidateService service = new NamespaceMemberCandidateService(
                namespaceService,
                namespaceAccessPolicy,
                userAccountRepository
        );

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(namespace);
        doNothing().when(namespaceService).assertAdminOrOwner(1L, "owner-1");
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(true);
        when(userAccountRepository.searchNonMembers(1L, "ali", PageRequest.of(0, 10)))
                .thenReturn(List.of(new UserAccount("user-2", "alina", "alina@example.com", null)));

        List<NamespaceCandidateUserResponse> result = service.searchCandidates("team-a", "ali", "owner-1", 10);

        assertEquals(1, result.size());
        assertEquals("user-2", result.getFirst().userId());
        verify(namespaceService).assertAdminOrOwner(1L, "owner-1");
    }

    @Test
    void searchCandidates_shouldRejectReadonlyNamespace() {
        Namespace namespace = new Namespace("team-a", "Team A", "owner-1");
        setField(namespace, "id", 1L);

        NamespaceMemberCandidateService service = new NamespaceMemberCandidateService(
                namespaceService,
                namespaceAccessPolicy,
                userAccountRepository
        );

        when(namespaceService.getNamespaceBySlug("team-a")).thenReturn(namespace);
        doNothing().when(namespaceService).assertAdminOrOwner(1L, "owner-1");
        when(namespaceAccessPolicy.canManageMembers(namespace)).thenReturn(false);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(false);

        assertThrows(DomainBadRequestException.class, () ->
                service.searchCandidates("team-a", "ali", "owner-1", 10));
    }

    @Test
    void searchCandidates_shouldRejectGlobalNamespaceBeforeMembershipChecks() {
        Namespace namespace = new Namespace("global", "Global", "system");
        setField(namespace, "id", 1L);
        namespace.setType(com.iflytek.skillhub.domain.namespace.NamespaceType.GLOBAL);

        NamespaceMemberCandidateService service = new NamespaceMemberCandidateService(
                namespaceService,
                namespaceAccessPolicy,
                userAccountRepository
        );

        when(namespaceService.getNamespaceBySlug("global")).thenReturn(namespace);
        when(namespaceAccessPolicy.isImmutable(namespace)).thenReturn(true);

        DomainBadRequestException exception = assertThrows(DomainBadRequestException.class, () ->
                service.searchCandidates("global", "ali", "guest-1", 10));

        assertEquals("error.namespace.system.immutable", exception.messageCode());
        verify(namespaceService, org.mockito.Mockito.never()).assertAdminOrOwner(1L, "guest-1");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
