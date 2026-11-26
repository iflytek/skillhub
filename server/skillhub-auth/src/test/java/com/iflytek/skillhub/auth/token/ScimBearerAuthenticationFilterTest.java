package com.iflytek.skillhub.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.auth.policy.RouteSecurityPolicyRegistry;
import com.iflytek.skillhub.domain.organization.DirectoryConnection;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionRepository;
import com.iflytek.skillhub.domain.organization.DirectoryConnectionSecretHasher;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class ScimBearerAuthenticationFilterTest {

    private static final String ORG = "org-1";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingBearerCredentialIsRejected() throws Exception {
        DirectoryConnectionRepository repository = mock(DirectoryConnectionRepository.class);
        ScimBearerAuthenticationFilter filter = new ScimBearerAuthenticationFilter(repository);
        MockHttpServletRequest request = request("/api/v1/organizations/" + ORG
                + "/directory-connections/conn-1/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validDirectoryTokenCreatesScimAuthentication() throws Exception {
        String token = "sh_scim_test-token";
        DirectoryConnection connection = DirectoryConnection.create(
                ORG, "admin", DirectoryConnectionSecretHasher.hash(token), "tokenhint", Instant.now());
        connection.activate(Instant.now().plusSeconds(1));
        DirectoryConnectionRepository repository = mock(DirectoryConnectionRepository.class);
        when(repository.findByOrganizationIdAndId(ORG, "conn-1")).thenReturn(Optional.of(connection));
        // The entity id is generated, so use its actual id in the request.
        String path = "/api/v1/organizations/" + ORG
                + "/directory-connections/" + connection.getId() + "/scim/v2/Users";
        when(repository.findByOrganizationIdAndId(ORG, connection.getId())).thenReturn(Optional.of(connection));
        ScimBearerAuthenticationFilter filter = new ScimBearerAuthenticationFilter(repository);
        MockHttpServletRequest request = request(path);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("scim:" + connection.getId());
        assertThat(RouteSecurityPolicyRegistry.isDirectoryScimPath(path)).isTrue();
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
