package com.iflytek.skillhub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.connection.oidc.OidcConnectionConfiguration;
import com.iflytek.skillhub.auth.connection.oidc.OidcLoginProtocolClient;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnection;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevision;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionRevisionRepository;
import com.iflytek.skillhub.auth.connection.persistence.LoginConnectionStatus;
import com.iflytek.skillhub.auth.federation.config.EnterpriseIdentityRolloutProperties;
import com.iflytek.skillhub.auth.oauth.OAuthLoginRedirectSupport;
import com.iflytek.skillhub.domain.organization.OrganizationRepository;
import com.iflytek.skillhub.domain.organization.OrganizationStatus;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Creates one-time, browser-bound enterprise OIDC authorization transactions. */
@Service
public class EnterpriseLoginStartService {
    private static final long TRANSACTION_TTL_SECONDS = 300;
    private static final String TRANSACTION_KEY_PREFIX = "skillhub:enterprise:oidc:tx:";
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "local v=redis.call('get',KEYS[1]); "
                    + "if not v then return nil end; "
                    + "local prefix=ARGV[1] .. '\\n'; "
                    + "if string.sub(v,1,string.len(prefix)) ~= prefix then return '__BINDING_MISMATCH__' end; "
                    + "redis.call('del',KEYS[1]); return string.sub(v,string.len(prefix)+1)",
            String.class);

    private final LoginConnectionRepository connections;
    private final LoginConnectionRevisionRepository revisions;
    private final OrganizationRepository organizations;
    private final EnterpriseIdentityRolloutProperties rollout;
    private final OidcLoginProtocolClient oidc;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public EnterpriseLoginStartService(
            LoginConnectionRepository connections,
            LoginConnectionRevisionRepository revisions,
            OrganizationRepository organizations,
            EnterpriseIdentityRolloutProperties rollout,
            OidcLoginProtocolClient oidc,
            ObjectMapper mapper,
            StringRedisTemplate redis
    ) {
        this.connections = connections;
        this.revisions = revisions;
        this.organizations = organizations;
        this.rollout = rollout;
        this.oidc = oidc;
        this.mapper = mapper;
        this.redis = redis;
    }

    public StartResult start(String publicHandle, String returnTo) {
        if (!rollout.isOidcLoginEnabled()) {
            throw unavailable();
        }
        LoginConnection connection = connections.findByPublicHandle(publicHandle)
                .filter(item -> item.getStatus() == LoginConnectionStatus.ACTIVE)
                .filter(item -> item.getActiveRevisionId() != null)
                .orElseThrow(this::unavailable);
        if (connection.getOrganizationId() == null
                || !rollout.getOrganizationAllowlist().contains(connection.getOrganizationId())
                || organizations.findById(connection.getOrganizationId())
                .filter(item -> item.getStatus() == OrganizationStatus.ACTIVE).isEmpty()) {
            throw unavailable();
        }
        LoginConnectionRevision revision = revisions.findById(connection.getActiveRevisionId())
                .orElseThrow(this::unavailable);
        OidcConnectionConfiguration config = revision.getConfiguration();
        if (revision.getSecretBindingVersion() == null || revision.getSecretBindingVersion() <= 0) {
            throw unavailable();
        }
        OidcLoginProtocolClient.ProviderMetadata metadata = oidc.metadata(config);
        URI callback = rollout.callbackUri(publicHandle);
        String state = token(32);
        String nonce = token(32);
        String verifier = token(48);
        String binding = token(32);
        String safeReturnTo = OAuthLoginRedirectSupport.sanitizeReturnTo(returnTo);
        EnterpriseLoginTransaction transaction = new EnterpriseLoginTransaction(
                state, publicHandle, connection.getId(), connection.getOrganizationId(), revision.getId(),
                nonce, verifier, binding, callback.toString(), safeReturnTo == null ? "/" : safeReturnTo);
        String key = TRANSACTION_KEY_PREFIX + state;
        try {
            redis.opsForValue().set(key, binding + "\n" + mapper.writeValueAsString(transaction),
                    TRANSACTION_TTL_SECONDS, TimeUnit.SECONDS);
            URI authorization = authorizationUri(metadata.authorizationEndpoint(), config.clientId(), callback,
                    state, nonce, verifier);
            return new StartResult(authorization, binding, transaction);
        } catch (JsonProcessingException invalid) {
            throw unavailable();
        }
    }

    public EnterpriseLoginTransaction consume(String state, String binding) {
        if (!isOpaqueToken(state) || !isOpaqueToken(binding)) {
            throw unavailable();
        }
        String value = redis.execute(CONSUME_SCRIPT, List.of(TRANSACTION_KEY_PREFIX + state), binding);
        if (value == null || "__BINDING_MISMATCH__".equals(value)) {
            throw unavailable();
        }
        try {
            return mapper.readValue(value, EnterpriseLoginTransaction.class);
        } catch (JsonProcessingException invalid) {
            throw unavailable();
        }
    }

    private static URI authorizationUri(URI endpoint, String clientId, URI callback,
                                        String state, String nonce, String verifier) {
        String query = String.join("&",
                parameter("response_type", "code"),
                parameter("client_id", clientId),
                parameter("redirect_uri", callback.toString()),
                parameter("scope", "openid profile email"),
                parameter("state", state),
                parameter("nonce", nonce),
                parameter("code_challenge", codeChallenge(verifier)),
                parameter("code_challenge_method", "S256"));
        return URI.create(endpoint + (endpoint.getQuery() == null ? "?" : "&") + query);
    }

    private static String parameter(String key, String value) {
        return URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String token(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static boolean isOpaqueToken(String value) {
        return value != null && value.length() >= 43 && value.length() <= 64
                && value.codePoints().allMatch(character -> character == '-'
                || character == '_' || character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z' || character >= '0' && character <= '9');
    }

    private static String codeChallenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private IllegalStateException unavailable() {
        return new IllegalStateException("enterprise login unavailable");
    }

    public record StartResult(URI authorizationUri, String browserBinding,
                              EnterpriseLoginTransaction transaction) {}

    public record EnterpriseLoginTransaction(String state, String publicHandle, String connectionId,
                                             String organizationId, String revisionId, String nonce,
                                             String codeVerifier, String browserBinding, String redirectUri,
                                             String returnTo) {}
}
