package com.iflytek.skillhub.config;

import com.iflytek.skillhub.domain.authoring.service.AuthoringSecurityPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * The production {@link AuthoringSecurityPolicy}, derived from
 * {@code skillhub.authoring.*} configuration.
 *
 * <p>SSRF posture: the server only makes outbound requests to public
 * addresses. Link-local addresses (which include the 169.254.169.254 cloud
 * metadata service), multicast, and the unspecified address are always
 * rejected. Loopback, RFC1918, unique-local IPv6, and CGNAT ranges are
 * rejected unless the surface-specific {@code allow-private-endpoints} flag
 * is set (used by local development to reach loopback fake servers).
 *
 * <p>Known limitation: the guard resolves the hostname at check time; the
 * HttpClient re-resolves at connect time, which mitigates (but cannot fully
 * eliminate) DNS-rebinding races. Binding the socket to the checked address
 * is not implemented because it would break virtual-hosted MCP endpoints.
 */
@Component
public class ConfiguredAuthoringSecurityPolicy implements AuthoringSecurityPolicy {

    private final AuthoringProperties properties;

    public ConfiguredAuthoringSecurityPolicy(AuthoringProperties properties) {
        this.properties = properties;
    }

    @Override
    public String endpointRejection(String url, EndpointUse use) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            return "malformed URL: " + url;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return "only http(s) endpoints are allowed, got: " + scheme;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL has no host: " + url;
        }
        boolean allowPrivate = use == EndpointUse.MCP_SERVER
                ? properties.getMcp().isAllowPrivateEndpoints()
                : properties.getOpenAiCompatible().isAllowPrivateEndpoints();

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            return "cannot resolve host '" + host + "'";
        }
        for (InetAddress address : addresses) {
            String reason = classify(address, allowPrivate);
            if (reason != null) {
                return reason;
            }
        }
        return null;
    }

    @Override
    public boolean stdioTransportAllowed() {
        return properties.getMcp().isStdioEnabled();
    }

    @Override
    public boolean envRefAllowed(String name) {
        return properties.getMcp().getEnvAllowlist().contains(name);
    }

    /** @return null when the address is allowed, otherwise the rejection reason. */
    private static String classify(InetAddress address, boolean allowPrivate) {
        if (address.isAnyLocalAddress()) {
            return "unspecified address is not a valid endpoint: " + address.getHostAddress();
        }
        if (address.isLinkLocalAddress()) {
            // 169.254.0.0/16 (incl. the cloud metadata service) and fe80::/10
            return "link-local address (cloud metadata range) is always blocked: "
                    + address.getHostAddress();
        }
        if (address.isMulticastAddress()) {
            return "multicast address is not a valid endpoint: " + address.getHostAddress();
        }
        boolean privateRange = address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || isUniqueLocalIpv6(address)
                || isCarrierGradeNat(address);
        if (privateRange && !allowPrivate) {
            return "private/loopback address is blocked unless allow-private-endpoints"
                    + " is enabled: " + address.getHostAddress();
        }
        return null;
    }

    /** fc00::/7 unique-local addresses are not covered by {@code isSiteLocalAddress}. */
    private static boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }

    /** 100.64.0.0/10 carrier-grade NAT, a common internal service range. */
    private static boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 0xFF) == 100
                && (bytes[1] & 0xFF) >= 64 && (bytes[1] & 0xFF) <= 127;
    }
}
