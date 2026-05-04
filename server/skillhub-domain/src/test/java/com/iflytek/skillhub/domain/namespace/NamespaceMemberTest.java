package com.iflytek.skillhub.domain.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamespaceMemberTest {

    @Test
    void constructorInitializesFields() {
        NamespaceMember member = new NamespaceMember(1L, "user-1", NamespaceRole.ADMIN);

        assertThat(member.getNamespaceId()).isEqualTo(1L);
        assertThat(member.getUserId()).isEqualTo("user-1");
        assertThat(member.getRole()).isEqualTo(NamespaceRole.ADMIN);
    }

    @Test
    void prePersistSetsTimestamps() {
        NamespaceMember member = new NamespaceMember(1L, "user-1", NamespaceRole.MEMBER);

        member.prePersist();

        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isNotNull();
        assertThat(member.getCreatedAt()).isEqualTo(member.getUpdatedAt());
    }

    @Test
    void preUpdateSetsUpdatedAt() {
        NamespaceMember member = new NamespaceMember(1L, "user-1", NamespaceRole.MEMBER);
        member.prePersist();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        member.preUpdate();

        assertThat(member.getUpdatedAt()).isAfterOrEqualTo(member.getCreatedAt());
    }

    @Test
    void settersWork() {
        NamespaceMember member = new NamespaceMember(1L, "user-1", NamespaceRole.MEMBER);

        member.setNamespaceId(2L);
        member.setUserId("user-2");
        member.setRole(NamespaceRole.OWNER);

        assertThat(member.getNamespaceId()).isEqualTo(2L);
        assertThat(member.getUserId()).isEqualTo("user-2");
        assertThat(member.getRole()).isEqualTo(NamespaceRole.OWNER);
    }

    @Test
    void protectedConstructorExistsForJpa() {
        NamespaceMember member = new NamespaceMember();
        assertThat(member).isNotNull();
    }
}
