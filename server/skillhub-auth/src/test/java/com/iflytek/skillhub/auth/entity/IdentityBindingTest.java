package com.iflytek.skillhub.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdentityBindingTest {

    @Test
    void protectedNoArgsConstructorSupportsJpaInstantiation() {
        IdentityBinding binding = new IdentityBinding();

        assertThat(binding.getId()).isNull();
        assertThat(binding.getUserId()).isNull();
        assertThat(binding.getProviderCode()).isNull();
        assertThat(binding.getSubject()).isNull();
        assertThat(binding.getLoginName()).isNull();
        assertThat(binding.getExtraJson()).isNull();
        assertThat(binding.getCreatedAt()).isNull();
        assertThat(binding.getUpdatedAt()).isNull();
    }

    @Test
    void constructorAndPrePersistPopulateCoreFields() {
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");

        assertThat(binding.getId()).isNull();
        assertThat(binding.getUserId()).isEqualTo("usr_1");
        assertThat(binding.getProviderCode()).isEqualTo("github");
        assertThat(binding.getSubject()).isEqualTo("gh_1");
        assertThat(binding.getLoginName()).isEqualTo("alice");
        assertThat(binding.getCreatedAt()).isNull();
        assertThat(binding.getUpdatedAt()).isNull();

        binding.prePersist();

        assertThat(binding.getCreatedAt()).isNotNull();
        assertThat(binding.getUpdatedAt()).isEqualTo(binding.getCreatedAt());
    }

    @Test
    void settersAndPreUpdateMutateTrackedFields() {
        IdentityBinding binding = new IdentityBinding("usr_1", "github", "gh_1", "alice");
        Instant seedUpdatedAt = Instant.EPOCH;

        binding.setUserId("usr_2");
        binding.setProviderCode("gitlab");
        binding.setSubject("gl_2");
        binding.setLoginName("bob");
        binding.setExtraJson("{\"avatar\":\"https://example.test/b.png\"}");
        ReflectionTestUtils.setField(binding, "createdAt", Instant.parse("2026-05-03T23:59:00Z"));
        ReflectionTestUtils.setField(binding, "updatedAt", seedUpdatedAt);

        binding.preUpdate();

        assertThat(binding.getUserId()).isEqualTo("usr_2");
        assertThat(binding.getProviderCode()).isEqualTo("gitlab");
        assertThat(binding.getSubject()).isEqualTo("gl_2");
        assertThat(binding.getLoginName()).isEqualTo("bob");
        assertThat(binding.getExtraJson()).isEqualTo("{\"avatar\":\"https://example.test/b.png\"}");
        assertThat(binding.getCreatedAt()).isEqualTo(Instant.parse("2026-05-03T23:59:00Z"));
        assertThat(binding.getUpdatedAt()).isAfter(seedUpdatedAt);
    }
}
