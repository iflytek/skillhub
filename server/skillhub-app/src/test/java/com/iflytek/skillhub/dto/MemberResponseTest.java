package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.user.UserAccount;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MemberResponseTest {

    private static void setId(NamespaceMember member, Long id) throws Exception {
        Field field = NamespaceMember.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(member, id);
    }

    private static void setCreatedAt(NamespaceMember member, Instant instant) throws Exception {
        Field field = NamespaceMember.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(member, instant);
    }

    private static void setUpdatedAt(NamespaceMember member, Instant instant) throws Exception {
        Field field = NamespaceMember.class.getDeclaredField("updatedAt");
        field.setAccessible(true);
        field.set(member, instant);
    }

    @Test
    void fromMember_only_mapsMemberFields() throws Exception {
        NamespaceMember member = new NamespaceMember(10L, "user-1", NamespaceRole.ADMIN);
        setId(member, 1L);
        setCreatedAt(member, Instant.parse("2024-01-01T00:00:00Z"));
        setUpdatedAt(member, Instant.parse("2024-02-01T00:00:00Z"));

        MemberResponse response = MemberResponse.from(member);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.namespaceId()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.displayName()).isNull();
        assertThat(response.email()).isNull();
        assertThat(response.role()).isEqualTo(NamespaceRole.ADMIN);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2024-02-01T00:00:00Z"));
    }

    @Test
    void fromMemberAndUser_mapsUserDisplayNameAndEmail_whenUserNotNull() throws Exception {
        NamespaceMember member = new NamespaceMember(20L, "user-2", NamespaceRole.MEMBER);
        setId(member, 2L);
        setCreatedAt(member, Instant.now());
        setUpdatedAt(member, Instant.now());
        UserAccount user = new UserAccount("user-2", "Charlie", "charlie@example.com", null);

        MemberResponse response = MemberResponse.from(member, user);

        assertThat(response.displayName()).isEqualTo("Charlie");
        assertThat(response.email()).isEqualTo("charlie@example.com");
    }

    @Test
    void fromMemberAndUser_usesNullDisplayNameAndEmail_whenUserIsNull() throws Exception {
        NamespaceMember member = new NamespaceMember(30L, "user-3", NamespaceRole.MEMBER);
        setId(member, 3L);
        setCreatedAt(member, Instant.now());
        setUpdatedAt(member, Instant.now());

        MemberResponse response = MemberResponse.from(member, null);

        assertThat(response.displayName()).isNull();
        assertThat(response.email()).isNull();
    }
}
