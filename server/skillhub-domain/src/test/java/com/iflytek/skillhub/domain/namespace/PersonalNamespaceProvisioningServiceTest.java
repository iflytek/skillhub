package com.iflytek.skillhub.domain.namespace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalNamespaceProvisioningServiceTest {

    private static final PersonalNamespaceOwner ALICE =
            new PersonalNamespaceOwner("usr_alice", "alice", "alice@example.com");

    @Mock
    private NamespaceService namespaceService;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @Mock
    private com.iflytek.skillhub.domain.user.UserAccountRepository userAccountRepository;

    private PersonalNamespaceProvisioningService service;
    private PersonalNamespaceProvisioningProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PersonalNamespaceProvisioningProperties();
        service = new PersonalNamespaceProvisioningService(
                properties,
                namespaceService,
                namespaceRepository,
                namespaceMemberRepository,
                userAccountRepository);
    }

    private void withSettings(boolean enabled, String slugTemplate, String displayNameTemplate) {
        properties.setEnabled(enabled);
        properties.setSlugTemplate(slugTemplate);
        properties.setDisplayNameTemplate(displayNameTemplate);
    }

    private void ownsNothing() {
        when(namespaceMemberRepository.findByUserId(ALICE.userId())).thenReturn(List.of());
    }

    /**
     * Mirrors {@link NamespaceService#createNamespace} returning the namespace it persisted.
     */
    private void namespaceCreationSucceeds() {
        when(namespaceService.createNamespace(any(), any(), any(), any()))
                .thenAnswer(invocation -> new Namespace(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(3)));
    }

    @Test
    void doesNothingWhenProvisioningIsDisabled() {
        withSettings(false, "${username}", "${username}");

        assertTrue(service.provisionFor(ALICE).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void skipsSystemAccount() {
        withSettings(true, "personal-${random}", "${username}-个人空间");
        when(userAccountRepository.findById("usr_system"))
                .thenReturn(Optional.of(com.iflytek.skillhub.domain.user.UserAccount
                        .systemAccount("usr_system", "system", null, null)));

        assertTrue(service.provisionFor(new PersonalNamespaceOwner("usr_system", "system", null)).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void defaultsAreEnabledForNewAccountProvisioning() {
        assertEquals(true, new PersonalNamespaceProvisioningProperties().isEnabled());
    }

    @Test
    void createsNamespaceFromTheConfiguredTemplate() {
        withSettings(true, "${username}-space", "${username}'s space");
        ownsNothing();
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice-space")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace("alice-space", "alice's space", null, "usr_alice");
    }

    @Test
    void appendsSuffixWhenTheSlugIsAlreadyTaken() {
        withSettings(true, "${username}", "${username}");
        ownsNothing();
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.of(new Namespace("alice", "Alice", "usr_x")));
        when(namespaceRepository.findBySlug("alice-2")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace(eq("alice-2"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void skipsReservedSlugsInsteadOfFailing() {
        PersonalNamespaceOwner admin = new PersonalNamespaceOwner("usr_admin", "admin", null);
        withSettings(true, "${username}", "${username}");
        when(namespaceMemberRepository.findByUserId(admin.userId())).thenReturn(List.of());
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("admin-2")).thenReturn(Optional.empty());

        service.provisionFor(admin);

        verify(namespaceService).createNamespace(eq("admin-2"), any(), isNull(), eq("usr_admin"));
    }

    @Test
    void skipsWhenTheUserAlreadyOwnsANamespace() {
        withSettings(true, "${username}", "${username}");
        when(namespaceMemberRepository.findByUserId(ALICE.userId()))
                .thenReturn(List.of(new NamespaceMember(7L, ALICE.userId(), NamespaceRole.OWNER)));
        when(namespaceRepository.findById(7L))
                .thenReturn(Optional.of(new Namespace("alice", "Alice", ALICE.userId())));

        assertTrue(service.provisionFor(ALICE).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void globalMembershipDoesNotCountAsOwningANamespace() {
        withSettings(true, "${username}", "${username}");
        Namespace global = new Namespace("global", "Global", "usr_system");
        global.setType(NamespaceType.GLOBAL);
        when(namespaceMemberRepository.findByUserId(ALICE.userId()))
                .thenReturn(List.of(new NamespaceMember(1L, ALICE.userId(), NamespaceRole.OWNER)));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(global));
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace(eq("alice"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void plainMembershipDoesNotCountAsOwningANamespace() {
        withSettings(true, "${username}", "${username}");
        when(namespaceMemberRepository.findByUserId(ALICE.userId()))
                .thenReturn(List.of(new NamespaceMember(3L, ALICE.userId(), NamespaceRole.MEMBER)));
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace(eq("alice"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void givesUpQuietlyWhenEveryCandidateSlugIsTaken() {
        withSettings(true, "${username}", "${username}");
        ownsNothing();
        when(namespaceRepository.findBySlug(any()))
                .thenReturn(Optional.of(new Namespace("taken", "Taken", "usr_x")));

        assertTrue(service.provisionFor(ALICE).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }
}
