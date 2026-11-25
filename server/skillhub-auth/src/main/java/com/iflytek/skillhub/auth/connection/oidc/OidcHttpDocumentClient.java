package com.iflytek.skillhub.auth.connection.oidc;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** Direct HTTPS requests with validated DNS results, no redirects, and bounded resources. */
final class OidcHttpDocumentClient implements AutoCloseable {
    private static final int MAX_DOCUMENT_BYTES = 128 * 1024;
    private static final int MAX_PARALLEL_REQUESTS = 4;
    private static final int DOCUMENT_TIMEOUT_SECONDS = 5;

    private final CloseableHttpClient client;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(
            0, MAX_PARALLEL_REQUESTS, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
            Thread.ofPlatform().daemon().name("oidc-connection-probe-", 1).factory());

    OidcHttpDocumentClient(EnterpriseOidcProperties properties) {
        client = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setConnectionFactory(ManagedHttpClientConnectionFactory.builder()
                                .http1Config(Http1Config.custom().setMaxHeaderCount(64).setMaxLineLength(8192).build())
                                .build())
                        .setDnsResolver(new OidcOutboundDnsResolver(properties.getPrivateHostAllowlist()))
                        .setMaxConnTotal(MAX_PARALLEL_REQUESTS)
                        .setMaxConnPerRoute(MAX_PARALLEL_REQUESTS)
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(3))
                                .setSocketTimeout(Timeout.ofSeconds(DOCUMENT_TIMEOUT_SECONDS))
                                .build())
                        .build())
                .setConnectionReuseStrategy((request, response, context) -> false)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableContentCompression()
                .disableCookieManagement()
                .disableAuthCaching()
                .build();
    }

    byte[] get(URI uri) {
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", "application/json, application/jwk-set+json");
        Future<byte[]> document;
        try {
            document = workers.submit(() -> read(request));
        } catch (RejectedExecutionException busy) {
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        }
        try {
            return document.get(DOCUMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            request.cancel();
            document.cancel(true);
            Thread.currentThread().interrupt();
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        } catch (TimeoutException timeout) {
            request.cancel();
            document.cancel(true);
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.TIMEOUT);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof OidcConnectionProbe.Failure safeFailure) {
                throw safeFailure;
            }
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        }
    }

    private byte[] read(HttpGet request) {
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                request.cancel();
                throw new OidcConnectionProbe.Failure(status >= 300 && status < 400
                        ? OidcConnectionProbe.Reason.BLOCKED
                        : OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
            }
            HttpEntity entity = response.getEntity();
            if (entity == null || entity.getContentLength() > MAX_DOCUMENT_BYTES) {
                request.cancel();
                throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.INVALID_METADATA);
            }
            try (InputStream stream = entity.getContent()) {
                byte[] bytes = stream.readNBytes(MAX_DOCUMENT_BYTES + 1);
                if (bytes.length > MAX_DOCUMENT_BYTES) {
                    request.cancel();
                    throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.INVALID_METADATA);
                }
                return bytes;
            }
        } catch (OidcOutboundDnsResolver.BlockedAddressException blocked) {
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.BLOCKED);
        } catch (SocketTimeoutException timeout) {
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.TIMEOUT);
        } catch (IOException ioFailure) {
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        }
    }

    @Override
    public void close() {
        workers.shutdownNow();
        client.close(CloseMode.IMMEDIATE);
    }
}
