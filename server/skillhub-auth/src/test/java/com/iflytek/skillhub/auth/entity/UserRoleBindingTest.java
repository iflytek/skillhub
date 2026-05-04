package com.iflytek.skillhub.auth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleBindingTest {

    @Test
    void protectedConstructor_shouldBeAccessible() {
        UserRoleBinding binding = new UserRoleBinding();
        assertNull(binding.getId());
        assertNull(binding.getUserId());
        assertNull(binding.getRole());
        assertNull(binding.getCreatedAt());
    }

    @Test
    void publicConstructor_shouldSetFields() {
        Role role = new Role();
        UserRoleBinding binding = new UserRoleBinding("user-1", role);

        assertNull(binding.getId());
        assertEquals("user-1", binding.getUserId());
        assertEquals(role, binding.getRole());
        assertNull(binding.getCreatedAt());
    }

    @Test
    void prePersist_shouldSetCreatedAt_whenNull() {
        Role role = new Role();
        UserRoleBinding binding = new UserRoleBinding("user-1", role);
        assertNull(binding.getCreatedAt());

        binding.prePersist();

        assertNotNull(binding.getCreatedAt());
    }

    @Test
    void prePersist_shouldSetCreatedAt() {
        Role role = new Role();
        UserRoleBinding binding = new UserRoleBinding("user-1", role);
        assertNull(binding.getCreatedAt());

        binding.prePersist();

        assertNotNull(binding.getCreatedAt());
    }

    @Test
    void setUserId_shouldUpdateUserId() {
        Role role = new Role();
        UserRoleBinding binding = new UserRoleBinding("user-1", role);
        binding.setUserId("user-2");
        assertEquals("user-2", binding.getUserId());
    }
}
