package com.iflytek.skillhub.auth.uass;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UassPropertiesCoverageTest {

    @Test
    void getAdminUsers_returnsEmptyListWhenNull() {
        UassProperties props = new UassProperties();
        props.setAdminUsers(null);
        assertThat(props.getAdminUsers()).isEmpty();
    }

    @Test
    void getAdminUsers_returnsCopyWhenSet() {
        UassProperties props = new UassProperties();
        UassProperties.AdminUserConfig config = new UassProperties.AdminUserConfig();
        config.setUssId("uss-1");
        props.setAdminUsers(List.of(config));
        assertThat(props.getAdminUsers()).hasSize(1);
        assertThat(props.getAdminUsers().get(0).getUssId()).isEqualTo("uss-1");
    }
}
