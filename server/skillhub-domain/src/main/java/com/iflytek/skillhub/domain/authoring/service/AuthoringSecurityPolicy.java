package com.iflytek.skillhub.domain.authoring.service;

/**
 * Security boundary for user-supplied runtime bindings, implemented by the
 * application layer from {@code skillhub.authoring.*} configuration. The domain
 * validator consults it at save time so that dangerous bindings are rejected
 * before they can ever run.
 *
 * <p>Implementations must be conservative: an endpoint, transport, or
 * environment reference that cannot be checked must be rejected, not allowed.
 */
public interface AuthoringSecurityPolicy {

    /** Where a user-supplied URL is about to be used, so flags can differ per surface. */
    enum EndpointUse {
        MCP_SERVER,
        LLM_API
    }

    /**
     * Decides whether the server may make an outbound HTTP(S) request to the
     * given URL (SSRF guard).
     *
     * @return {@code null} when the URL is allowed, otherwise the rejection reason
     */
    String endpointRejection(String url, EndpointUse use);

    /** Whether MCP servers may use the stdio transport (server-side process spawn). */
    boolean stdioTransportAllowed();

    /** Whether a binding may reference the named server environment variable. */
    boolean envRefAllowed(String name);
}
