package com.iflytek.skillhub.auth.connection.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.nimbusds.jose.jwk.JWKSet;
import java.net.URI;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OidcMetadataManagerTest {

    private static final OidcIssuer ISSUER = new OidcIssuer("https://id.example.com");
    private static final Instant NOW = Instant.parse("2026-09-08T06:00:00Z");

    @Test
    void cacheIsRevisionScopedAndExpiresDeterministically() {
        CountingSource source = new CountingSource();
        OidcMetadataManager manager = manager(source);

        manager.get(key(1), ISSUER, NOW);
        manager.get(key(1), ISSUER, NOW.plusSeconds(59));
        manager.get(key(2), ISSUER, NOW.plusSeconds(59));
        manager.get(key(1), ISSUER, NOW.plusSeconds(61));

        assertThat(source.loads).isEqualTo(3);
    }

    @Test
    void productionDefaultsUseBoundedTtlAndKidMissCooldown() {
        assertThat(OidcMetadataManager.DEFAULT_TIME_TO_LIVE)
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(OidcMetadataManager.DEFAULT_KID_MISS_REFRESH_COOLDOWN)
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void kidMissRefreshesOnceAndThenUsesRotatedKey() {
        CountingSource source = new CountingSource();
        OidcMetadataManager manager = manager(source);

        assertThat(manager.resolveKey(key(1), ISSUER, "new-key", NOW).getKeyID())
                .isEqualTo("new-key");
        assertThat(manager.resolveKey(key(1), ISSUER, "new-key", NOW.plusSeconds(1)).getKeyID())
                .isEqualTo("new-key");

        assertThat(source.loads).isEqualTo(1);
        assertThat(source.keyRefreshes).isEqualTo(1);
    }

    @Test
    void repeatedUnknownKidWithinCooldownCannotTriggerUnboundedRemoteRequests() {
        CountingSource source = new CountingSource();
        OidcMetadataManager manager = manager(source);

        assertThatThrownBy(() -> manager.resolveKey(key(1), ISSUER, "missing-a", NOW))
                .isInstanceOf(OidcMetadataUnavailableException.class);
        assertThatThrownBy(() -> manager.resolveKey(
                key(1), ISSUER, "missing-b", NOW.plusSeconds(1)
        )).isInstanceOf(OidcMetadataUnavailableException.class);

        assertThat(source.loads).isEqualTo(1);
        assertThat(source.keyRefreshes).isEqualTo(1);
    }

    @Test
    void concurrentKidMissesShareOneRemoteRefresh() throws Exception {
        CountDownLatch callersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        AtomicInteger refreshes = new AtomicInteger();
        OidcMetadataSource source = new OidcMetadataSource() {
            @Override
            public OidcMetadataBundle load(OidcIssuer issuer) {
                return new OidcMetadataBundle(metadata(), keys("old-key"));
            }

            @Override
            public OidcJwkSetSnapshot refreshKeys(OidcProviderMetadata metadata) {
                refreshes.incrementAndGet();
                refreshStarted.countDown();
                await(releaseRefresh);
                return keys("new-key");
            }
        };
        OidcMetadataManager manager = manager(source);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> resolve = () -> {
                callersReady.countDown();
                await(start);
                return manager.resolveKey(key(1), ISSUER, "new-key", NOW).getKeyID();
            };
            Future<String> first = executor.submit(resolve);
            Future<String> second = executor.submit(resolve);
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(refreshStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releaseRefresh.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("new-key");
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("new-key");
            assertThat(refreshes).hasValue(1);
        } finally {
            start.countDown();
            releaseRefresh.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void invalidatingConnectionDropsAllRevisionEntries() {
        CountingSource source = new CountingSource();
        OidcMetadataManager manager = manager(source);
        manager.get(key(1), ISSUER, NOW);
        manager.get(key(2), ISSUER, NOW);

        manager.invalidateConnection("organization-1", "connection-1");
        manager.get(key(1), ISSUER, NOW.plusSeconds(1));
        manager.get(key(2), ISSUER, NOW.plusSeconds(1));

        assertThat(source.loads).isEqualTo(4);
    }

    private static OidcMetadataManager manager(OidcMetadataSource source) {
        RemoteIdentityIoExecutor direct = new RemoteIdentityIoExecutor() {
            @Override
            public <T> T execute(Supplier<T> operation) {
                return operation.get();
            }
        };
        return new OidcMetadataManager(
                source,
                direct,
                Duration.ofMinutes(1),
                Duration.ofSeconds(30)
        );
    }

    private static OidcMetadataCacheKey key(long revision) {
        return new OidcMetadataCacheKey("organization-1", "connection-1", revision);
    }

    private static final class CountingSource implements OidcMetadataSource {
        private int loads;
        private int keyRefreshes;

        @Override
        public OidcMetadataBundle load(OidcIssuer issuer) {
            loads++;
            return new OidcMetadataBundle(metadata(), keys("old-key"));
        }

        @Override
        public OidcJwkSetSnapshot refreshKeys(OidcProviderMetadata metadata) {
            keyRefreshes++;
            return keys("new-key");
        }
    }

    private static OidcProviderMetadata metadata() {
        return new OidcProviderMetadata(
                ISSUER,
                URI.create("https://id.example.com/authorize"),
                URI.create("https://id.example.com/token"),
                URI.create("https://id.example.com/jwks"),
                Set.of("RS256")
        );
    }

    private static OidcJwkSetSnapshot keys(String keyId) {
        try {
            return new OidcJwkSetSnapshot(
                    JWKSet.parse(DefaultOidcMetadataSourceTest.jwks(keyId)).getKeys()
            );
        } catch (ParseException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test thread interrupted", exception);
        }
    }
}
