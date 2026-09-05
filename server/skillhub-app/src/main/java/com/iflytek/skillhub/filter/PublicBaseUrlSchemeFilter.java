package com.iflytek.skillhub.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Some TLS-terminating gateways (e.g. Higress) forward requests over plain HTTP without a
 * usable X-Forwarded-Proto, so the container reports scheme http. When the public base URL is
 * https, force the scheme back to https for requests matching the public host; otherwise
 * {baseUrl} expansion (OAuth2 redirect URIs) and Secure session cookies break.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PublicBaseUrlSchemeFilter extends OncePerRequestFilter {

    private final String publicHost;

    public PublicBaseUrlSchemeFilter(@Value("${skillhub.public.base-url:}") String publicBaseUrl) {
        this.publicHost = resolveHttpsHost(publicBaseUrl);
    }

    private static String resolveHttpsHost(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        URI uri = URI.create(publicBaseUrl.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return null;
        }
        return uri.getHost().toLowerCase();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (publicHost == null
                || !"http".equals(request.getScheme())
                || !publicHost.equals(request.getServerName().toLowerCase())) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new HttpsSchemeRequest(request), response);
    }

    private static final class HttpsSchemeRequest extends HttpServletRequestWrapper {

        private HttpsSchemeRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getScheme() {
            return "https";
        }

        @Override
        public boolean isSecure() {
            return true;
        }

        @Override
        public int getServerPort() {
            int port = super.getServerPort();
            return port == 80 ? 443 : port;
        }

        @Override
        public StringBuffer getRequestURL() {
            HttpServletRequest request = (HttpServletRequest) getRequest();
            StringBuffer url = new StringBuffer("https://").append(request.getServerName());
            String uri = request.getRequestURI();
            if (uri != null) {
                url.append(uri);
            }
            return url;
        }
    }
}
