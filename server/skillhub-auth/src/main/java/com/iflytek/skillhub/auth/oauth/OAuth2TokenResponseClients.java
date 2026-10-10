package com.iflytek.skillhub.auth.oauth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

/**
 * Factory for the standard OAuth2 authorization-code token client used by public providers.
 *
 * <p>Spring's default client does not cap response size. Public provider endpoints should still be
 * treated as untrusted remote IO, so the shared client keeps the standard parser but adds timeouts
 * and a bounded response body before any converter sees the payload.
 */
final class OAuth2TokenResponseClients {

    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private OAuth2TokenResponseClients() {
    }

    static DefaultAuthorizationCodeTokenResponseClient standard() {
        DefaultAuthorizationCodeTokenResponseClient client =
                new DefaultAuthorizationCodeTokenResponseClient();
        client.setRestOperations(standardRestTemplate());
        return client;
    }

    /** Package-visible so tests can exercise the production HTTP policy directly. */
    static RestTemplate standardRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);

        RestTemplate template = new RestTemplate(List.of(
                new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter()
        ));
        template.setRequestFactory(factory);
        template.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        template.getInterceptors().add((request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                response.close();
                throw new IOException("OAuth2 token response exceeds "
                        + MAX_RESPONSE_BYTES + " bytes");
            }
            return new BoundedClientHttpResponse(response, bytes);
        });
        return template;
    }

    /** Replays the already-read, size-checked body so Spring's converters can parse it normally. */
    private record BoundedClientHttpResponse(ClientHttpResponse delegate, byte[] body)
            implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
