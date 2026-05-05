package com.iflytek.skillhub.auth.device;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceCodeDataTest {

    @Test
    void noArgConstructor_createsInstance() {
        DeviceCodeData data = new DeviceCodeData();
        assertThat(data).isNotNull();
    }

    @Test
    void fullConstructor_setsFields() {
        DeviceCodeData data = new DeviceCodeData("dc-1", "uc-1", DeviceCodeStatus.PENDING, "user-1");
        assertThat(data.getDeviceCode()).isEqualTo("dc-1");
        assertThat(data.getUserCode()).isEqualTo("uc-1");
        assertThat(data.getStatus()).isEqualTo(DeviceCodeStatus.PENDING);
        assertThat(data.getUserId()).isEqualTo("user-1");
    }

    @Test
    void setters_updateFields() {
        DeviceCodeData data = new DeviceCodeData();
        data.setStatus(DeviceCodeStatus.AUTHORIZED);
        data.setUserId("user-2");
        assertThat(data.getStatus()).isEqualTo(DeviceCodeStatus.AUTHORIZED);
        assertThat(data.getUserId()).isEqualTo("user-2");
    }
}
