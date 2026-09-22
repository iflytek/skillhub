package com.iflytek.skillhub.auth.connection.oidc;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** DNS seam used to validate every outbound OIDC request target before connecting. */
@FunctionalInterface
public interface OidcHostAddressResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;

    static OidcHostAddressResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
