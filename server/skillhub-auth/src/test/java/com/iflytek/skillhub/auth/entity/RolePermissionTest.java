package com.iflytek.skillhub.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RolePermissionTest {

    @Test
    void rolePermissionIdEqualsAndHashCode() {
        RolePermission.RolePermissionId id1 = new RolePermission.RolePermissionId();
        ReflectionTestUtils.setField(id1, "roleId", 1L);
        ReflectionTestUtils.setField(id1, "permissionId", 2L);

        RolePermission.RolePermissionId id2 = new RolePermission.RolePermissionId();
        ReflectionTestUtils.setField(id2, "roleId", 1L);
        ReflectionTestUtils.setField(id2, "permissionId", 2L);

        RolePermission.RolePermissionId id3 = new RolePermission.RolePermissionId();
        ReflectionTestUtils.setField(id3, "roleId", 1L);
        ReflectionTestUtils.setField(id3, "permissionId", 3L);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1).isEqualTo(id1);
        assertThat(id1).isNotEqualTo(null);
        assertThat(id1).isNotEqualTo("string");
    }

    @Test
    void rolePermissionIdDefaultConstructor() {
        RolePermission.RolePermissionId id = new RolePermission.RolePermissionId();
        assertThat(id).isNotNull();
    }

    @Test
    void rolePermissionIdWithNullFieldsEquals() {
        RolePermission.RolePermissionId id1 = new RolePermission.RolePermissionId();
        RolePermission.RolePermissionId id2 = new RolePermission.RolePermissionId();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void rolePermissionGetters() {
        RolePermission rp = new RolePermission();
        assertThat(rp.getRoleId()).isNull();
        assertThat(rp.getPermissionId()).isNull();
    }
}
