package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OidcOutboundDnsResolverTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "127.0.0.1", "169.254.169.254", "100.64.0.1",
            "192.0.2.1", "198.18.1.1", "224.0.0.1", "240.0.0.1",
            "::", "::1", "fe80::1", "ff02::1", "2001:db8::1", "2002::1", "fd00:ec2::254"
    })
    void neverPermitsReservedAddresses(String value) throws Exception {
        var address = InetAddress.getByName(value);

        assertThat(OidcOutboundDnsResolver.isAllowed(address, false)).isFalse();
        assertThat(OidcOutboundDnsResolver.isAllowed(address, true)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.1.2.3", "172.31.1.5", "192.168.1.2", "fd01::1"})
    void requiresAnExplicitApprovedHostForPrivateAddresses(String value) throws Exception {
        var address = InetAddress.getByName(value);

        assertThat(OidcOutboundDnsResolver.isAllowed(address, false)).isFalse();
        assertThat(OidcOutboundDnsResolver.isAllowed(address, true)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "2606:4700::1"})
    void permitsPublicAddressesWithoutAConfigOverride(String value) throws Exception {
        assertThat(OidcOutboundDnsResolver.isAllowed(InetAddress.getByName(value), false)).isTrue();
    }
}
