package com.iflytek.skillhub.auth.token;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionRepository;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionSecretHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates SCIM bearer credentials without falling back to a browser session. */
@Component
public class ScimBearerAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern PATH = Pattern.compile(
            "^/api/v1/organizations/([^/]+)/directory-connections/([^/]+)/scim/v2(?:/.*)?$");

    private final DirectoryConnectionRepository connections;

    public ScimBearerAuthenticationFilter(DirectoryConnectionRepository connections) {
        this.connections = connections;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Matcher matcher = PATH.matcher(RouteSecurityPolicyRegistry.requestPath(request));
        if (!matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response);
            return;
        }
        String rawToken = authorization.substring(7).trim();
        if (rawToken.isEmpty()) {
            reject(response);
            return;
        }
        DirectoryConnection connection = connections.findByOrganizationIdAndId(
                        matcher.group(1), matcher.group(2))
                .orElse(null);
        if (connection == null || !connection.acceptsSecret(DirectoryConnectionSecretHasher.hash(rawToken))) {
            reject(response);
            return;
        }
        var authentication = new UsernamePasswordAuthenticationToken(
                "scim:" + connection.getId(),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_SCIM_DIRECTORY")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RouteSecurityPolicyRegistry.isDirectoryScimPath(
                RouteSecurityPolicyRegistry.requestPath(request));
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/scim+json");
        response.getWriter().write("{\"schemas\":[\"urn:ietf:params:scim:api:messages:2.0:Error\"],\"detail\":\"Invalid SCIM bearer credential\",\"status\":\"401\"}");
    }
}
