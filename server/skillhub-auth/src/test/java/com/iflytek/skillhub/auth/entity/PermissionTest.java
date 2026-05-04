package com.iflytek.skillhub.auth.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionTest {

    @Test
    void getters_shouldReturnValues() {
        Permission permission = new Permission();
        assertNull(permission.getId());
        assertNull(permission.getCode());
        assertNull(permission.getName());
    }
}
