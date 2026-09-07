package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureOidcDocumentClientTest {

    private static final URI PUBLIC_URI = URI.create(
            "https://id.example.com/.well-known/openid-configuration"
    );
    private static final InetAddress PUBLIC_ADDRESS = address("93.184.216.34");
    private static final InetAddress PRIVATE_ADDRESS = address("10.0.0.8");

    @Test
    void derivesDiscoveryUrlAndRejectsNonCanonicalIssuerInputs() {
        assertThat(new OidcIssuer("https://id.example.com/tenant/").discoveryUri())
                .isEqualTo(URI.create(
                        "https://id.example.com/tenant/.well-known/openid-configuration"
                ));

        assertThatThrownBy(() -> new OidcIssuer("http://id.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OidcIssuer("https://user@id.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OidcIssuer("https://ID.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OidcIssuer("https://id.example.com?tenant=a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateInitialTargetBeforeNetworkIo() {
        RecordingTransport transport = new RecordingTransport();
        SecureOidcDocumentClient client = client(
                host -> List.of(PRIVATE_ADDRESS),
                Set.of(),
                transport
        );

        assertUnavailable(() -> client.get(PUBLIC_URI, OidcDocumentKind.METADATA));
        assertThat(transport.requestedUris).isEmpty();
    }

    @Test
    void revalidatesRedirectTargetAndBlocksPrivateAddress() {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(response(
                302,
                Map.of("Location", List.of("https://internal.example.com/metadata")),
                new byte[0]
        ));
        OidcHostAddressResolver resolver = host -> host.equals("id.example.com")
                ? List.of(PUBLIC_ADDRESS)
                : List.of(PRIVATE_ADDRESS);
        SecureOidcDocumentClient client = client(resolver, Set.of(), transport);

        assertUnavailable(() -> client.get(PUBLIC_URI, OidcDocumentKind.METADATA));
        assertThat(transport.requestedUris).containsExactly(PUBLIC_URI);
    }

    @Test
    void operatorExactHostAllowlistCanReachPrivateSelfHostedProvider() {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(jsonResponse("{\"issuer\":\"https://id.corp.example\"}"));
        SecureOidcDocumentClient client = client(
                host -> List.of(PRIVATE_ADDRESS),
                Set.of("id.corp.example"),
                transport
        );

        byte[] body = client.get(
                URI.create("https://id.corp.example/.well-known/openid-configuration"),
                OidcDocumentKind.METADATA
        );

        assertThat(new String(body, StandardCharsets.UTF_8)).contains("id.corp.example");
    }

    @Test
    void operatorPrivateHostAllowlistRejectsPatternsUrlsAndIpLiterals() {
        OidcHostAddressResolver resolver = host -> List.of(PRIVATE_ADDRESS);

        assertThatThrownBy(() -> new OidcOutboundTargetPolicy(
                resolver,
                Set.of("*.corp.example")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OidcOutboundTargetPolicy(
                resolver,
                Set.of("https://id.corp.example")
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OidcOutboundTargetPolicy(
                resolver,
                Set.of("10.0.0.8")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedResponseAndClosesStream() {
        TrackingInputStream stream = new TrackingInputStream(
                new byte[OidcDocumentKind.METADATA.maxBytes() + 1]
        );
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(response(
                200,
                Map.of("Content-Type", List.of("application/json")),
                stream
        ));
        SecureOidcDocumentClient client = client(
                host -> List.of(PUBLIC_ADDRESS),
                Set.of(),
                transport
        );

        assertUnavailable(() -> client.get(PUBLIC_URI, OidcDocumentKind.METADATA));
        assertThat(stream.closed).isTrue();
    }

    @Test
    void timeoutFailsClosedWithoutLeakingTransportCause() {
        OidcSingleHopHttpTransport transport = (uri, timeout) -> {
            assertThat(timeout).isEqualTo(Duration.ofSeconds(5));
            throw new HttpTimeoutException("attacker-controlled-host timed out");
        };
        SecureOidcDocumentClient client = client(
                host -> List.of(PUBLIC_ADDRESS),
                Set.of(),
                transport
        );

        assertUnavailable(() -> client.get(PUBLIC_URI, OidcDocumentKind.METADATA));
    }

    @Test
    void rejectsMediaTypesThatOnlyShareTheJsonPrefix() {
        RecordingTransport transport = new RecordingTransport();
        transport.responses.add(response(
                200,
                Map.of("Content-Type", List.of("application/json-patch+json")),
                "{}".getBytes(StandardCharsets.UTF_8)
        ));
        SecureOidcDocumentClient client = client(
                host -> List.of(PUBLIC_ADDRESS),
                Set.of(),
                transport
        );

        assertUnavailable(() -> client.get(PUBLIC_URI, OidcDocumentKind.METADATA));
    }

    @Test
    void refusesMoreThanThreeRedirects() {
        RecordingTransport transport = new RecordingTransport();
        for (int index = 1; index <= 4; index++) {
            transport.responses.add(response(
                    302,
                    Map.of("Location", List.of("https://id.example.com/redirect-" + index)),
                    new byte[0]
            ));
        }
        SecureOidcDocumentClient client = client(
                host -> List.of(PUBLIC_ADDRESS),
                Set.of(),
                transport
        );

        assertUnavailable(() -> client.get(PUBLIC_URI, OidcDocumentKind.METADATA));
        assertThat(transport.requestedUris).hasSize(4);
    }

    private static SecureOidcDocumentClient client(
            OidcHostAddressResolver resolver,
            Set<String> allowedPrivateHosts,
            OidcSingleHopHttpTransport transport
    ) {
        return new SecureOidcDocumentClient(
                new OidcOutboundTargetPolicy(resolver, allowedPrivateHosts),
                transport
        );
    }

    private static OidcHttpResponse jsonResponse(String json) {
        return response(
                200,
                Map.of("Content-Type", List.of("application/json; charset=utf-8")),
                json.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static OidcHttpResponse response(
            int status,
            Map<String, List<String>> headers,
            byte[] body
    ) {
        return response(status, headers, new ByteArrayInputStream(body));
    }

    private static OidcHttpResponse response(
            int status,
            Map<String, List<String>> headers,
            ByteArrayInputStream body
    ) {
        return new OidcHttpResponse(status, headers, body);
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertUnavailable(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(OidcMetadataUnavailableException.class)
                .hasMessage("OIDC provider metadata is unavailable")
                .hasNoCause();
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private static final class RecordingTransport implements OidcSingleHopHttpTransport {
        private final Queue<OidcHttpResponse> responses = new ArrayDeque<>();
        private final List<URI> requestedUris = new ArrayList<>();

        @Override
        public OidcHttpResponse get(URI uri, Duration timeout) {
            requestedUris.add(uri);
            OidcHttpResponse response = responses.poll();
            if (response == null) {
                throw new AssertionError("Unexpected request: " + uri);
            }
            return response;
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] buffer) {
            super(buffer);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
