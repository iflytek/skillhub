package com.iflytek.skillhub.auth.connection.oidc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** JDK HTTP transport with TLS verification and automatic redirects disabled. */
public final class JdkOidcSingleHopHttpTransport implements OidcSingleHopHttpTransport {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient client;

    public JdkOidcSingleHopHttpTransport() {
        this(CONNECT_TIMEOUT);
    }

    JdkOidcSingleHopHttpTransport(Duration connectTimeout) {
        this(HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    JdkOidcSingleHopHttpTransport(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public OidcHttpResponse get(URI uri, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(Objects.requireNonNull(uri, "uri"))
                .timeout(Objects.requireNonNull(timeout, "timeout"))
                .header("Accept", "application/json, application/jwk-set+json")
                .GET()
                .build();
        HttpResponse<java.io.InputStream> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );
        return new OidcHttpResponse(
                response.statusCode(),
                response.headers().map(),
                response.body()
        );
    }
}
