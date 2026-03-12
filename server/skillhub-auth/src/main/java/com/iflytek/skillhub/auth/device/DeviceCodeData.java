package com.iflytek.skillhub.auth.device;

import java.io.Serializable;

public class DeviceCodeData implements Serializable {
    private String deviceCode;
    private String userCode;
    private DeviceCodeStatus status;
    private String userId;

    public DeviceCodeData() {}

    public DeviceCodeData(String deviceCode, String userCode,
                          DeviceCodeStatus status, String userId) {
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.status = status;
        this.userId = userId;
    }

    public String getDeviceCode() { return deviceCode; }
    public String getUserCode() { return userCode; }
    public DeviceCodeStatus getStatus() { return status; }
    public void setStatus(DeviceCodeStatus status) { this.status = status; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
