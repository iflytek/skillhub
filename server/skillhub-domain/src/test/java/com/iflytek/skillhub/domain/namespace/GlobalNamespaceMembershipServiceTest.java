package com.iflytek.skillhub.domain.namespace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GlobalNamespaceMembershipServiceTest {

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    private GlobalNamespaceMembershipService service;

    @BeforeEach
    void setUp() {
        service = new GlobalNamespaceMembershipService(namespaceRepository, namespaceMemberRepository);
    }

    @Test
    void ensureMember_createsGlobalMembershipWhenMissing() throws Exception {
        Namespace global = new Namespace("global", "Global", "system");
        setNamespaceId(global, 1L);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(global));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_1")).thenReturn(Optional.empty());

        service.ensureMember("usr_1");

        ArgumentCaptor<NamespaceMember> memberCaptor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getNamespaceId()).isEqualTo(1L);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo("usr_1");
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(NamespaceRole.MEMBER);
    }

    @Test
    void ensureMember_keepsExistingGlobalMembership() throws Exception {
        Namespace global = new Namespace("global", "Global", "system");
        setNamespaceId(global, 1L);
        NamespaceMember existing = new NamespaceMember(1L, "usr_1", NamespaceRole.ADMIN);

        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(global));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_1")).thenReturn(Optional.of(existing));

        service.ensureMember("usr_1");

        verify(namespaceMemberRepository, never()).save(any());
    }

    private void setNamespaceId(Namespace namespace, Long id) throws Exception {
        Field field = Namespace.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(namespace, id);
    }
}
