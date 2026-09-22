package com.iflytek.skillhub.auth.connection.oidc;

import com.iflytek.skillhub.auth.federation.adapter.RemoteIdentityIoExecutor;
import com.nimbusds.jose.jwk.JWK;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Revision-keyed metadata cache with bounded refresh on an unknown token key id. */
public final class OidcMetadataManager {

    private static final int LOCK_STRIPES = 64;
    static final Duration DEFAULT_TIME_TO_LIVE = Duration.ofMinutes(15);
    static final Duration DEFAULT_KID_MISS_REFRESH_COOLDOWN = Duration.ofSeconds(30);

    private final OidcMetadataSource source;
    private final RemoteIdentityIoExecutor remoteIdentityIo;
    private final Duration timeToLive;
    private final Duration kidMissRefreshCooldown;
    private final Map<OidcMetadataCacheKey, CachedBundle> cache = new ConcurrentHashMap<>();
    private final Map<OidcMetadataCacheKey, Instant> lastKidMissRefresh = new ConcurrentHashMap<>();
    private final Object[] locks = new Object[LOCK_STRIPES];

    public OidcMetadataManager(
            OidcMetadataSource source,
            RemoteIdentityIoExecutor remoteIdentityIo
    ) {
        this(
                source,
                remoteIdentityIo,
                DEFAULT_TIME_TO_LIVE,
                DEFAULT_KID_MISS_REFRESH_COOLDOWN
        );
    }

    public OidcMetadataManager(
            OidcMetadataSource source,
            RemoteIdentityIoExecutor remoteIdentityIo,
            Duration timeToLive,
            Duration kidMissRefreshCooldown
    ) {
        this.source = Objects.requireNonNull(source, "source");
        this.remoteIdentityIo = Objects.requireNonNull(remoteIdentityIo, "remoteIdentityIo");
        this.timeToLive = requireDuration(timeToLive, Duration.ofSeconds(30), Duration.ofHours(24));
        this.kidMissRefreshCooldown = requireDuration(
                kidMissRefreshCooldown,
                Duration.ofSeconds(5),
                Duration.ofMinutes(5)
        );
        java.util.Arrays.setAll(locks, ignored -> new Object());
    }

    public OidcMetadataBundle get(
            OidcMetadataCacheKey key,
            OidcIssuer issuer,
            Instant now
    ) {
        OidcMetadataCacheKey requiredKey = Objects.requireNonNull(key, "key");
        Instant currentTime = Objects.requireNonNull(now, "now");
        CachedBundle current = cache.get(requiredKey);
        if (current != null && current.expiresAt().isAfter(currentTime)) {
            requireIssuer(current.bundle(), issuer);
            return current.bundle();
        }
        synchronized (lockFor(requiredKey)) {
            current = cache.get(requiredKey);
            if (current != null && current.expiresAt().isAfter(currentTime)) {
                requireIssuer(current.bundle(), issuer);
                return current.bundle();
            }
            OidcMetadataBundle loaded = safeRemote(() -> source.load(issuer));
            requireIssuer(loaded, issuer);
            cache.put(requiredKey, new CachedBundle(
                    loaded,
                    currentTime,
                    currentTime.plus(timeToLive)
            ));
            return loaded;
        }
    }

    public JWK resolveKey(
            OidcMetadataCacheKey key,
            OidcIssuer issuer,
            String keyId,
            Instant now
    ) {
        OidcMetadataBundle current = get(key, issuer, now);
        if (current.jwkSet().containsKeyId(keyId)) {
            return current.jwkSet().findByKeyId(keyId);
        }
        synchronized (lockFor(key)) {
            CachedBundle latest = cache.get(key);
            if (latest != null && latest.bundle().jwkSet().containsKeyId(keyId)) {
                return latest.bundle().jwkSet().findByKeyId(keyId);
            }
            Instant lastRefresh = lastKidMissRefresh.get(key);
            if (lastRefresh != null && now.isBefore(lastRefresh.plus(kidMissRefreshCooldown))) {
                throw new OidcMetadataUnavailableException();
            }
            OidcJwkSetSnapshot refreshed = safeRemote(
                    () -> source.refreshKeys(
                            latest == null ? current.metadata() : latest.bundle().metadata()
                    )
            );
            lastKidMissRefresh.put(key, now);
            OidcMetadataBundle replacement = new OidcMetadataBundle(
                    latest == null ? current.metadata() : latest.bundle().metadata(),
                    refreshed
            );
            cache.put(key, new CachedBundle(replacement, now, now.plus(timeToLive)));
            return refreshed.findByKeyId(keyId);
        }
    }

    public void invalidate(OidcMetadataCacheKey key) {
        cache.remove(Objects.requireNonNull(key, "key"));
        lastKidMissRefresh.remove(key);
    }

    public void invalidateConnection(String scopeKey, String connectionId) {
        String requiredScope = requireText(scopeKey, "scopeKey");
        String requiredConnection = requireText(connectionId, "connectionId");
        cache.keySet().removeIf(key -> key.scopeKey().equals(requiredScope)
                && key.connectionId().equals(requiredConnection));
        lastKidMissRefresh.keySet().removeIf(key -> key.scopeKey().equals(requiredScope)
                && key.connectionId().equals(requiredConnection));
    }

    private static void requireIssuer(OidcMetadataBundle bundle, OidcIssuer issuer) {
        if (!bundle.metadata().issuer().equals(Objects.requireNonNull(issuer, "issuer"))) {
            throw new OidcMetadataUnavailableException();
        }
    }

    private static Duration requireDuration(Duration value, Duration minimum, Duration maximum) {
        Duration duration = Objects.requireNonNull(value, "value");
        if (duration.compareTo(minimum) < 0 || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("OIDC cache duration is outside safe bounds");
        }
        return duration;
    }

    private <T> T safeRemote(java.util.function.Supplier<T> operation) {
        try {
            return remoteIdentityIo.execute(operation);
        } catch (OidcMetadataUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new OidcMetadataUnavailableException();
        }
    }

    private Object lockFor(OidcMetadataCacheKey key) {
        int index = (key.hashCode() & Integer.MAX_VALUE) % locks.length;
        return locks[index];
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private record CachedBundle(
            OidcMetadataBundle bundle,
            Instant fetchedAt,
            Instant expiresAt
    ) {
    }
}
