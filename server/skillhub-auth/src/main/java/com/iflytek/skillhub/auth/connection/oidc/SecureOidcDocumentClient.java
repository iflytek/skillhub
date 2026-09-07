package com.iflytek.skillhub.auth.connection.oidc;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Bounded redirect-aware fetcher that verifies every outbound target before one-hop I/O. */
public final class SecureOidcDocumentClient {

    static final int MAX_REDIRECTS = 3;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final OidcOutboundTargetPolicy targetPolicy;
    private final OidcSingleHopHttpTransport transport;

    public SecureOidcDocumentClient(
            OidcOutboundTargetPolicy targetPolicy,
            OidcSingleHopHttpTransport transport
    ) {
        this.targetPolicy = Objects.requireNonNull(targetPolicy, "targetPolicy");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public byte[] get(URI initialUri, OidcDocumentKind kind) {
        URI current = Objects.requireNonNull(initialUri, "initialUri");
        OidcDocumentKind documentKind = Objects.requireNonNull(kind, "kind");
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            targetPolicy.verify(current);
            try (OidcHttpResponse response = transport.get(current, REQUEST_TIMEOUT)) {
                if (response.status() == 200) {
                    requireJsonContentType(response.firstHeader("Content-Type"));
                    byte[] bytes = response.body().readNBytes(documentKind.maxBytes() + 1);
                    if (bytes.length > documentKind.maxBytes()) {
                        throw new OidcMetadataUnavailableException();
                    }
                    return bytes;
                }
                if (!REDIRECT_STATUSES.contains(response.status()) || redirects == MAX_REDIRECTS) {
                    throw new OidcMetadataUnavailableException();
                }
                String location = response.firstHeader("Location");
                if (location == null) {
                    throw new OidcMetadataUnavailableException();
                }
                current = current.resolve(location);
            } catch (OidcMetadataUnavailableException exception) {
                throw exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new OidcMetadataUnavailableException();
            } catch (IOException | RuntimeException exception) {
                throw new OidcMetadataUnavailableException();
            } catch (Exception exception) {
                throw new OidcMetadataUnavailableException();
            }
        }
        throw new OidcMetadataUnavailableException();
    }

    private static void requireJsonContentType(String contentType) {
        if (contentType == null) {
            throw new OidcMetadataUnavailableException();
        }
        String normalized = contentType.split(";", 2)[0].trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (!normalized.equals("application/json")
                && !normalized.equals("application/jwk-set+json")) {
            throw new OidcMetadataUnavailableException();
        }
    }
}
