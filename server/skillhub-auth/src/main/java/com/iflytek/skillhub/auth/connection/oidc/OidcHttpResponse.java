package com.iflytek.skillhub.auth.connection.oidc;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One non-followed HTTP response; callers must close the body. */
public record OidcHttpResponse(int status, Map<String, List<String>> headers, InputStream body)
        implements AutoCloseable {

    public OidcHttpResponse {
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers"));
        body = Objects.requireNonNull(body, "body");
    }

    public String firstHeader(String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse(null);
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
