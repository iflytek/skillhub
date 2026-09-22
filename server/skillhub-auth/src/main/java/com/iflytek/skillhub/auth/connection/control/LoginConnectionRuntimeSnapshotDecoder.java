package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.core.LoginConnectionRuntimeSnapshot;

/** Adapter-owned decoder for one persisted Login Connection contract major. */
public interface LoginConnectionRuntimeSnapshotDecoder {

    AdapterKey adapterKey();

    int contractMajor();

    LoginConnectionRuntimeSnapshot<?> decode(LoginConnectionRevisionSnapshot revision);
}
