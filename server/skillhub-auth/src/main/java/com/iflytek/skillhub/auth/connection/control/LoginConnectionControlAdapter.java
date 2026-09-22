package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.AdapterKey;
import com.iflytek.skillhub.auth.connection.secret.SecretPurpose;
import java.util.Optional;

/** Reviewed control-plane companion for one versioned login Adapter family. */
public interface LoginConnectionControlAdapter {

    AdapterKey adapterKey();

    /** Secret slot described by this adapter, if the protocol needs one. */
    Optional<SecretPurpose> secretPurpose();

    PreparedLoginConnectionRevision prepare(LoginConnectionConfigurationInput configuration);

    LoginConnectionTestProbe testProbe();
}
