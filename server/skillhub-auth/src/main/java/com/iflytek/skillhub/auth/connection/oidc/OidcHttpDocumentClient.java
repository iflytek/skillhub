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
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** Direct HTTPS requests with validated DNS results, no redirects, and bounded resources. */
class OidcHttpDocumentClient implements AutoCloseable {
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
        return get(uri, java.util.Map.of());
    }

    byte[] get(URI uri, java.util.Map<String, String> headers) {
        HttpGet request = new HttpGet(uri);
        request.setHeader("Accept", "application/json, application/jwk-set+json");
        headers.forEach(request::setHeader);
        return execute(request);
    }

    byte[] postForm(URI uri, java.util.Map<String, String> form) {
        HttpPost request = new HttpPost(uri);
        request.setHeader("Accept", "application/json");
        java.util.List<NameValuePair> values = form.entrySet().stream()
                .map(entry -> new BasicNameValuePair(entry.getKey(), entry.getValue()))
                .map(pair -> (NameValuePair) pair)
                .toList();
        request.setEntity(new UrlEncodedFormEntity(values, java.nio.charset.StandardCharsets.UTF_8));
        return execute(request);
    }

    private byte[] execute(org.apache.hc.core5.http.ClassicHttpRequest request) {
        Future<byte[]> document;
        try {
            document = workers.submit(() -> read(request));
        } catch (RejectedExecutionException busy) {
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        }
        try {
            return document.get(DOCUMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            cancel(request);
            document.cancel(true);
            Thread.currentThread().interrupt();
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        } catch (TimeoutException timeout) {
            cancel(request);
            document.cancel(true);
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.TIMEOUT);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof OidcConnectionProbe.Failure safeFailure) {
                throw safeFailure;
            }
            throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
        }
    }

    private byte[] read(ClassicHttpRequest request) {
        try (ClassicHttpResponse response = client.executeOpen(null, request, null)) {
            int status = response.getCode();
            if (status < 200 || status >= 300) {
                cancel(request);
                throw new OidcConnectionProbe.Failure(status >= 300 && status < 400
                        ? OidcConnectionProbe.Reason.BLOCKED
                        : OidcConnectionProbe.Reason.UPSTREAM_UNAVAILABLE);
            }
            HttpEntity entity = response.getEntity();
            if (entity == null || entity.getContentLength() > MAX_DOCUMENT_BYTES) {
                cancel(request);
                throw new OidcConnectionProbe.Failure(OidcConnectionProbe.Reason.INVALID_METADATA);
            }
            try (InputStream stream = entity.getContent()) {
                byte[] bytes = stream.readNBytes(MAX_DOCUMENT_BYTES + 1);
                if (bytes.length > MAX_DOCUMENT_BYTES) {
                    cancel(request);
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

    private static void cancel(org.apache.hc.core5.http.ClassicHttpRequest request) {
        if (request instanceof org.apache.hc.client5.http.classic.methods.HttpUriRequestBase cancellable) {
            cancellable.cancel();
        }
    }

    @Override
    public void close() {
        workers.shutdownNow();
        client.close(CloseMode.IMMEDIATE);
    }
}
