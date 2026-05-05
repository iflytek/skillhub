package com.iflytek.skillhub.domain.namespace;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NamespaceTest {

    @Test
    void constructorInitializesFields() {
        Namespace ns = new Namespace("my-team", "My Team", "user-1");

        assertThat(ns.getSlug()).isEqualTo("my-team");
        assertThat(ns.getDisplayName()).isEqualTo("My Team");
        assertThat(ns.getCreatedBy()).isEqualTo("user-1");
        assertThat(ns.getStatus()).isEqualTo(NamespaceStatus.ACTIVE);
        assertThat(ns.getType()).isEqualTo(NamespaceType.TEAM);
    }

    @Test
    void prePersistSetsTimestamps() {
        Namespace ns = new Namespace("test", "Test", "user-1");

        ns.prePersist();

        assertThat(ns.getCreatedAt()).isNotNull();
        assertThat(ns.getUpdatedAt()).isNotNull();
        assertThat(ns.getCreatedAt()).isEqualTo(ns.getUpdatedAt());
    }

    @Test
    void preUpdateSetsUpdatedAt() {
        Namespace ns = new Namespace("test", "Test", "user-1");
        ns.prePersist();

        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {}
        ns.preUpdate();

        assertThat(ns.getUpdatedAt()).isAfterOrEqualTo(ns.getCreatedAt());
    }

    @Test
    void gettersAndSettersWork() {
        Namespace ns = new Namespace("slug", "Name", "user-1");
        ns.prePersist();

        assertThat(ns.getId()).isNull();
        assertThat(ns.getSlug()).isEqualTo("slug");
        assertThat(ns.getDisplayName()).isEqualTo("Name");
        assertThat(ns.getCreatedBy()).isEqualTo("user-1");
        assertThat(ns.getCreatedAt()).isNotNull();
        assertThat(ns.getUpdatedAt()).isNotNull();

        assertThat(ns.getDescription()).isNull();
        ns.setDescription("desc");
        assertThat(ns.getDescription()).isEqualTo("desc");

        assertThat(ns.getAvatarUrl()).isNull();
        ns.setAvatarUrl("https://example.com/avatar.png");
        assertThat(ns.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");

        ns.setStatus(NamespaceStatus.FROZEN);
        assertThat(ns.getStatus()).isEqualTo(NamespaceStatus.FROZEN);

        ns.setType(NamespaceType.GLOBAL);
        assertThat(ns.getType()).isEqualTo(NamespaceType.GLOBAL);
    }

    @Test
    void protectedConstructorExistsForJpa() {
        Namespace ns = new Namespace();
        assertThat(ns).isNotNull();
    }
}
