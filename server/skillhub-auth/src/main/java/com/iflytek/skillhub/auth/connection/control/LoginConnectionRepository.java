package com.iflytek.skillhub.auth.connection.control;

import com.iflytek.skillhub.auth.connection.core.ConnectionHandle;
import java.util.Optional;

/** Organization-scoped persistence port for Login Connection lifecycle state. */
public interface LoginConnectionRepository {

    Optional<LoginConnection> findByOrganizationIdAndId(String organizationId, String id);

    /** Serializes high-risk control-plane mutations such as Secret rotation. */
    Optional<LoginConnection> lockByOrganizationIdAndId(String organizationId, String id);

    /** Dedicated data-plane lookup; the random handle reveals no tenant management identifier. */
    Optional<LoginConnection> findByPublicHandle(ConnectionHandle handle);

    LoginConnection save(LoginConnection connection);
}
