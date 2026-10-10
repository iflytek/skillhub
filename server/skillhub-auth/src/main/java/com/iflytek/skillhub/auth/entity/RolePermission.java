package com.iflytek.skillhub.auth.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "role_permission")
@IdClass(RolePermission.RolePermissionId.class)
public class RolePermission {
    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    public Long getRoleId() { return roleId; }
    public Long getPermissionId() { return permissionId; }

    public static class RolePermissionId implements Serializable {
        private Long roleId;
        private Long permissionId;

        public RolePermissionId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePermissionId that)) return false;
            return Objects.equals(roleId, that.roleId) && Objects.equals(permissionId, that.permissionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permissionId);
        }
    }
}
