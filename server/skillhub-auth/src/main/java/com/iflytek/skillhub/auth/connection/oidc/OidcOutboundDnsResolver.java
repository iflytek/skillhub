package com.iflytek.skillhub.auth.connection.oidc;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.apache.hc.client5.http.DnsResolver;

/** The HTTP transport connects to these checked addresses without a second DNS lookup. */
final class OidcOutboundDnsResolver implements DnsResolver {

    private static final byte[] CLOUD_METADATA_IPV6 = {
            (byte) 0xfd, 0, 0x0e, (byte) 0xc2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02, 0x54
    };

    private final Set<String> privateHosts;

    OidcOutboundDnsResolver(Set<String> privateHosts) {
        this.privateHosts = Set.copyOf(privateHosts);
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(host);
        boolean permitPrivate = privateHosts.contains(host.toLowerCase(Locale.ROOT));
        if (addresses.length == 0 || addresses.length > 16
                || Arrays.stream(addresses).anyMatch(address -> !isAllowed(address, permitPrivate))) {
            throw new BlockedAddressException();
        }
        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }

    static boolean isAllowed(InetAddress address, boolean permitPrivate) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (Arrays.equals(bytes, CLOUD_METADATA_IPV6)) {
            return false;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);
        if (bytes.length == 4) {
            if (first == 10 || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)) {
                return permitPrivate;
            }
            return first != 0 && first < 224
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && (third == 0 || third == 2))
                    && !(first == 192 && second == 88 && third == 99)
                    && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }
        if ((first & 0xfe) == 0xfc) {
            return permitPrivate;
        }
        // Serve global unicast ranges, excluding transition, documentation and special-use prefixes.
        return (first & 0xe0) == 0x20
                && !(first == 0x20 && second == 0x02)
                && !(first == 0x20 && second == 0x01
                    && (third <= 0x01 || (third == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8)))
                && !(first == 0x3f && (second & 0xf0) == 0xf0);
    }

    static final class BlockedAddressException extends UnknownHostException {
    }
}
