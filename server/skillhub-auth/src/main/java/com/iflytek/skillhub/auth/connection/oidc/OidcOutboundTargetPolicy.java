package com.iflytek.skillhub.auth.connection.oidc;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** HTTPS and IP policy for metadata/JWKS SSRF control. */
public final class OidcOutboundTargetPolicy {

    private final OidcHostAddressResolver resolver;
    private final Set<String> operatorAllowedPrivateHosts;

    public OidcOutboundTargetPolicy(
            OidcHostAddressResolver resolver,
            Set<String> operatorAllowedPrivateHosts
    ) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.operatorAllowedPrivateHosts = Set.copyOf(
                Objects.requireNonNull(operatorAllowedPrivateHosts, "operatorAllowedPrivateHosts")
                        .stream()
                        .map(OidcOutboundTargetPolicy::normalizeAllowedPrivateHost)
                        .toList()
        );
    }

    public void verify(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme())
                || host == null
                || host.isBlank()
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            throw new OidcMetadataUnavailableException();
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(normalizedHost).toArray(InetAddress[]::new);
        } catch (UnknownHostException | RuntimeException exception) {
            throw new OidcMetadataUnavailableException();
        }
        if (addresses.length == 0) {
            throw new OidcMetadataUnavailableException();
        }
        if (!operatorAllowedPrivateHosts.contains(normalizedHost)) {
            for (InetAddress address : addresses) {
                if (!isPublic(address)) {
                    throw new OidcMetadataUnavailableException();
                }
            }
        }
    }

    private static String normalizeAllowedPrivateHost(String host) {
        Objects.requireNonNull(host, "operatorAllowedPrivateHosts entry");
        String normalized = host.toLowerCase(Locale.ROOT);
        if (!host.equals(host.trim())
                || normalized.isBlank()
                || normalized.length() > 253
                || normalized.startsWith(".")
                || normalized.endsWith(".")
                || normalized.indexOf('*') >= 0
                || normalized.chars().anyMatch(character -> character > 0x7f)) {
            throw new IllegalArgumentException(
                    "OIDC private host allowlist entries must be exact ASCII hostnames"
            );
        }
        try {
            URI parsed = new URI("https", null, normalized, -1, null, null, null);
            if (!normalized.equals(parsed.getHost()) || looksLikeIpLiteral(normalized)) {
                throw new IllegalArgumentException(
                        "OIDC private host allowlist entries must be exact ASCII hostnames"
                );
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(
                    "OIDC private host allowlist entries must be exact ASCII hostnames"
            );
        }
        return normalized;
    }

    private static boolean looksLikeIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.chars().allMatch(character ->
                character == '.' || character >= '0' && character <= '9'
        );
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && third == 0)
                    && !(first == 192 && second == 0 && third == 2)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20
                    && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8;
            return !uniqueLocal && !documentation;
        }
        return false;
    }
}
