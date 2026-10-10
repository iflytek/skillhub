package com.iflytek.skillhub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.config.SkillPublishProperties;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class BuiltinSkillRemotePackageDownloaderTest {

    @Test
    void acceptsAllowedHttpsCdnHostsOnly() {
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://bjcdn.openstorage.cn/a.zip")))
                .isTrue();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://assets.bjcdn.openstorage.cn/a.zip")))
                .isTrue();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("http://bjcdn.openstorage.cn/a.zip")))
                .isFalse();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://evil.com/a.zip")))
                .isFalse();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://user:pass@bjcdn.openstorage.cn/a.zip")))
                .isFalse();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://bjcdn.openstorage.cn:8443/a.zip")))
                .isFalse();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://127.0.0.1/a.zip")))
                .isFalse();
        assertThat(BuiltinSkillRemotePackageDownloader.isAllowedUrl(URI.create("https://localhost/a.zip")))
                .isFalse();
    }

    @Test
    void defaultHttpClientDoesNotFollowRedirects() {
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(new SkillPublishProperties());

        assertThat(downloader.httpClient().followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(downloader.httpClient().connectTimeout()).contains(Duration.ofSeconds(5));
    }

    @Test
    void downloadsAllowedUrlWithThirtySecondRequestTimeout() {
        FakeHttpClient client = new FakeHttpClient(200, new byte[] {1, 2, 3});
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(
                new SkillPublishProperties(),
                client
        );

        Optional<byte[]> bytes = downloader.download(URI.create("https://bjcdn.openstorage.cn/package.zip"));

        assertThat(bytes).contains(new byte[] {1, 2, 3});
        assertThat(client.lastRequest.timeout()).contains(Duration.ofSeconds(30));
    }

    @Test
    void rejectsRedirectResponsesWithoutReadingLocation() {
        FakeHttpClient client = new FakeHttpClient(302, new byte[] {1});
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(
                new SkillPublishProperties(),
                client
        );

        Optional<byte[]> bytes = downloader.download(URI.create("https://bjcdn.openstorage.cn/package.zip"));

        assertThat(bytes).isEmpty();
        assertThat(client.sendCalls).isEqualTo(1);
    }

    @Test
    void closesNonSuccessResponseBody() {
        CloseAwareInputStream body = new CloseAwareInputStream(new byte[] {1});
        FakeHttpClient client = new FakeHttpClient(500, body);
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(
                new SkillPublishProperties(),
                client
        );

        Optional<byte[]> bytes = downloader.download(URI.create("https://bjcdn.openstorage.cn/package.zip"));

        assertThat(bytes).isEmpty();
        assertThat(body.closed()).isTrue();
    }

    @Test
    void rejectedUrlDoesNotSendHttpRequest() {
        FakeHttpClient client = new FakeHttpClient(200, new byte[] {1});
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(
                new SkillPublishProperties(),
                client
        );

        Optional<byte[]> bytes = downloader.download(URI.create("https://example.com/package.zip"));

        assertThat(bytes).isEmpty();
        assertThat(client.sendCalls).isZero();
    }

    @Test
    void stopsReadingWhenResponseExceedsMaxPackageSize() {
        SkillPublishProperties properties = new SkillPublishProperties();
        properties.setMaxPackageSize(2);
        FakeHttpClient client = new FakeHttpClient(200, new byte[] {1, 2, 3});
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(properties, client);

        assertThat(downloader.download(URI.create("https://bjcdn.openstorage.cn/package.zip"))).isEmpty();
    }

    @Test
    void returnsEmptyWhenResponseBodyStopsBeforeCompletion() throws Exception {
        BlockingInputStream body = new BlockingInputStream();
        FakeHttpClient client = new FakeHttpClient(200, body);
        BuiltinSkillRemotePackageDownloader downloader = new BuiltinSkillRemotePackageDownloader(
                new SkillPublishProperties(),
                client,
                Duration.ofMillis(50)
        );
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Optional<byte[]>> result = executor.submit(
                () -> downloader.download(URI.create("https://bjcdn.openstorage.cn/package.zip")));

        try {
            assertThat(result.get(1, TimeUnit.SECONDS)).isEmpty();
            assertThat(body.closed()).isTrue();
        } finally {
            body.close();
            executor.shutdownNow();
        }
    }

    static class FakeHttpClient extends HttpClient {

        private final int statusCode;
        private final InputStream body;
        private HttpRequest lastRequest;
        private int sendCalls;

        FakeHttpClient(int statusCode, byte[] body) {
            this(statusCode, new ByteArrayInputStream(body));
        }

        FakeHttpClient(int statusCode, InputStream body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(5));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            lastRequest = request;
            sendCalls++;
            @SuppressWarnings("unchecked")
            T responseBody = (T) body;
            return new FakeResponse<>(request, statusCode, responseBody);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }

    static final class CloseAwareInputStream extends ByteArrayInputStream {

        private boolean closed;

        private CloseAwareInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean closed() {
            return closed;
        }
    }

    static final class BlockingInputStream extends InputStream {

        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public int read() {
            waitUntilClosed();
            return -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            waitUntilClosed();
            return -1;
        }

        @Override
        public void close() {
            closed.set(true);
        }

        boolean closed() {
            return closed.get();
        }

        private void waitUntilClosed() {
            while (!closed.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    record FakeResponse<T>(HttpRequest request, int statusCode, T body) implements HttpResponse<T> {
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}
