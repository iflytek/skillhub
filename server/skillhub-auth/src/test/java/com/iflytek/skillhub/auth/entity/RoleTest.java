package com.iflytek.skillhub.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @Test
    void prePersist_shouldSetCreatedAt_whenNull() {
        Role role = new Role();
        assertNull(role.getCreatedAt());

        role.prePersist();

        assertNotNull(role.getCreatedAt());
    }

    @Test
    void prePersist_shouldSetCreatedAt() {
        Role role = new Role();
        assertNull(role.getCreatedAt());

        role.prePersist();

        assertNotNull(role.getCreatedAt());
    }

    @Test
    void getters_shouldReturnDefaultValues() {
        Role role = new Role();
        assertNull(role.getId());
        assertNull(role.getCode());
        assertNull(role.getName());
        assertFalse(role.isSystem());
        assertNull(role.getCreatedAt());
    }
}
