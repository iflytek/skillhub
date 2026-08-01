package com.iflytek.skillhub.auth.ldap;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.entity.IdentityBinding;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.IdentityBindingRepository;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.security.cert.CertificateException;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import javax.net.ssl.SSLException;
import javax.naming.AuthenticationException;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.regex.Pattern;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

/**
 * Handles LDAP authentication for enterprise directory integration.
 * <p>
 * Identity is anchored on a stable directory identifier (entryUUID/objectGUID) via
 * {@link IdentityBinding} (provider="ldap"), not on the user's email. This prevents
 * silent account merging when an LDAP user's email collides with an existing
 * local/OAuth account, and avoids duplicate accounts for email-less users.
 */
@Service
@ConditionalOnProperty(prefix = "skillhub.ldap", name = "enabled", havingValue = "true")
public class LdapAuthService {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthService.class);
    private static final String LDAP_PROVIDER = "ldap";
    // Allows alphanumeric, underscore, hyphen, dot, and @ (for UPN formats), 3-64 characters.
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_@.\\-]{3,64}$");
    // LDAP attribute names only allow ASCII letters, digits, and hyphens.
    private static final Pattern ATTRIBUTE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9-]*$");

    /**
     * Signals that a concurrent first login wrote the same LDAP subject binding first. The
     * provisioning (sub-)transaction has already been rolled back cleanly; callers must
     * re-resolve the identity by subject in a fresh transaction.
     */
    private static final class LdapBindingRaceException extends RuntimeException {
        LdapBindingRaceException(Throwable cause) {
            super(cause);
        }
    }

    private final LdapProperties ldapProperties;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final GlobalNamespaceMembershipService globalNamespaceMembershipService;
    private final IdentityBindingRepository identityBindingRepository;
    private final EntityManager entityManager;
    /**
     * Programmatic REQUIRES_NEW template for account/binding provisioning. A separate physical
     * transaction is required so a failed concurrent insert (which marks its transaction
     * rollback-only) can be rolled back cleanly and the identity re-resolved in a fresh
     * transaction. Spring's annotation-driven propagation cannot be used here because the
     * provisioning methods are invoked internally (self-invocation bypasses the proxy).
     */
    private final TransactionTemplate ldapProvisioningTx;

    public LdapAuthService(LdapProperties ldapProperties,
                           UserAccountRepository userAccountRepository,
                           UserRoleBindingRepository userRoleBindingRepository,
                           GlobalNamespaceMembershipService globalNamespaceMembershipService,
                           IdentityBindingRepository identityBindingRepository,
                           EntityManager entityManager,
                           PlatformTransactionManager transactionManager) {
        this.ldapProperties = ldapProperties;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.globalNamespaceMembershipService = globalNamespaceMembershipService;
        this.identityBindingRepository = identityBindingRepository;
        this.entityManager = entityManager;
        this.ldapProvisioningTx = new TransactionTemplate(transactionManager);
        this.ldapProvisioningTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Authenticates a user against the LDAP server.
     * If the user doesn't exist in the local database, creates a new user based on LDAP attributes.
     *
    * @param username the username
    * @param password the password
    * @return PlatformPrincipal if authentication succeeds
    * @throws AuthFlowException if authentication fails
    */
    public PlatformPrincipal login(String username, String password) {
        log.debug("Starting LDAP authentication for username: {}", username);

        if (!ldapProperties.isEnabled()) {
            log.warn("LDAP authentication is not enabled");
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.disabled");
        }

        // Validate all LDAP attribute names that flow into JNDI calls to prevent filter/attribute
        // injection via operator misconfiguration. These names are operator-controlled, not user input.
        validateAttributeNames();

        log.debug("LDAP host: {}, base: {}, searchBase: {}, searchAttr: {}",
            safeLogHost(ldapProperties.getUrl()),
            ldapProperties.getBase(),
            ldapProperties.getUserSearchBase(),
            ldapProperties.getUserSearchAttribute());

        // First, try to find the user in LDAP and authenticate
        String userDn = findUserDn(username);
        log.debug("LDAP findUserDn result for {}: {}", username, userDn != null);
        
        if (userDn == null) {
            log.warn("User {} not found in LDAP directory", username);
            throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.ldap.userNotFound");
        }

        // Authenticate against LDAP
        log.debug("Attempting LDAP bind for user DN: {}", userDn);
        boolean authenticated = authenticateLdap(userDn, password);
        log.debug("LDAP bind result for {}: {}", username, authenticated);
        
        if (!authenticated) {
            log.warn("LDAP authentication failed for username: {}", username);
            throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.ldap.invalidCredentials");
        }

        // Get user attributes from LDAP
        log.debug("Fetching user attributes from LDAP for DN: {}", userDn);
        Attributes userAttributes = getUserAttributes(userDn);
        if (userAttributes == null) {
            log.error("Failed to fetch user attributes from LDAP for DN: {}", userDn);
            // Bind already succeeded, so the credentials are valid. This is a transient directory
            // failure; surface a 503 with the directoryUnavailable message instead of masking it
            // as a 401 "invalid credentials" (which would mislead the user about the password).
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.directoryUnavailable");
        }

        // Find or create local user account anchored on the stable LDAP subject. Account and
        // binding creation run in their own (sub-)transaction; a concurrent first login for the
        // same subject is recovered by re-resolving the identity in a fresh transaction.
        log.debug("Finding or creating local user account for username: {}", username);
        UserAccount user;
        try {
            user = ldapProvisioningTx.execute(status -> findOrCreateLdapUser(username, userAttributes));
        } catch (LdapBindingRaceException e) {
            log.warn("Concurrent first login detected for LDAP subject of username {}; resolving existing account", username);
            user = ldapProvisioningTx.execute(status -> resolveReturningUser(userAttributes, username));
        }

        // Check if user can login (status check)
        log.debug("Checking user status for user: {}, status: {}", username, user.getStatus());
        ensureUserCanLogin(user);

        log.debug("LDAP authentication successful for username: {}", username);
        return buildPrincipal(user);
    }

    /**
     * Ensures the user account status allows login.
     */
    private void ensureUserCanLogin(UserAccount user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.accountDisabled");
        }
        if (user.getStatus() == UserStatus.PENDING) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.accountPending");
        }
        if (user.getStatus() == UserStatus.MERGED) {
            throw new AuthFlowException(HttpStatus.FORBIDDEN, "error.auth.local.accountMerged");
        }
    }

    /**
     * Finds the DN (Distinguished Name) of a user in LDAP.
     */
    private String findUserDn(String username) {
        // LDAP injection prevention: validate username before search
        if (!isValidUsername(username)) {
            log.warn("Invalid username format for LDAP search: {}", username);
            return null;
        }
        String searchAttr = ldapProperties.getUserSearchAttribute();

        DirContext ctx = null;
        javax.naming.NamingEnumeration<SearchResult> results = null;
        try {
            ctx = createLdapContext();
            String searchFilter = "(" + searchAttr + "={0})";
            String searchBase = ldapProperties.getUserSearchBase().isEmpty()
                ? ldapProperties.getBase()
                : ldapProperties.getUserSearchBase() + "," + ldapProperties.getBase();

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[0]);

            results = ctx.search(searchBase, searchFilter, new Object[]{username}, searchControls);

            if (results.hasMore()) {
                SearchResult result = results.next();
                return result.getNameInNamespace();
            }
            return null;
        } catch (CommunicationException e) {
            if (isTlsFailure(e)) {
                log.warn("LDAP TLS/certificate failure while searching for user {}: {}", username, e.getMessage());
                throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.tlsError");
            }
            log.warn("LDAP directory unavailable while searching for user {}: {}", username, e.getMessage());
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.directoryUnavailable");
        } catch (AuthenticationException e) {
            log.warn("LDAP bind authentication failed while searching for user {}: {}", username, e.getMessage());
            throw new AuthFlowException(HttpStatus.UNAUTHORIZED, "error.auth.ldap.invalidCredentials");
        } catch (NamingException e) {
            log.warn("LDAP naming error while searching for user {}: {}", username, e.getMessage());
            return null;
        } finally {
            // Close NamingEnumeration to prevent resource leaks
            if (results != null) {
                try {
                    results.close();
                } catch (Exception e) {
                    log.warn("Failed to close LDAP search results", e);
                }
            }
            closeContext(ctx);
        }
    }

    /**
     * Creates an LDAP context for searching.
     */
    private DirContext createLdapContext() throws NamingException {
        // Bind-account context for directory searches/reads; delegates to the shared factory.
        return createLdapContext(null, null);
    }

    /**
     * Creates an LDAP context authenticated with the given principal/credentials. When both are
     {@code null}, falls back to the configured bind account (or anonymous if none is set). This is
     the single place that builds the JNDI environment, so connection/timeout settings stay
     consistent across search, bind, and attribute-read operations.
     */
    private DirContext createLdapContext(String principal, String credentials) throws NamingException {
        return new InitialDirContext(buildJndiEnvironment(principal, credentials));
    }

    /**
     * Builds the JNDI environment for one LDAP operation. Package-private so tests can assert
     * connection/timeout/TLS settings without opening a real directory connection.
     */
    Hashtable<String, String> buildJndiEnvironment(String principal, String credentials) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapProperties.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        // Connection timeout: configurable (default 5 seconds for connect, 10 for read)
        env.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(ldapProperties.getConnectTimeoutMillis()));
        env.put("com.sun.jndi.ldap.read.timeout", String.valueOf(ldapProperties.getReadTimeoutMillis()));
        // Explicit principal/credentials take precedence; otherwise use the configured bind account.
        String bindPrincipal = (principal != null) ? principal : ldapProperties.getUsername();
        String bindCredentials = (principal != null) ? credentials : ldapProperties.getPassword();
        if (bindPrincipal != null && !bindPrincipal.isEmpty()) {
            env.put(Context.SECURITY_PRINCIPAL, bindPrincipal);
            env.put(Context.SECURITY_CREDENTIALS, bindCredentials);
        }
        // LDAPS certificate validation uses the JVM default trust store unless a custom store is
        // configured; this lets operators trust internal/self-signed directory CAs.
        String trustStorePath = ldapProperties.getTlsTrustStorePath();
        if (trustStorePath != null && !trustStorePath.isEmpty()) {
            env.put("javax.net.ssl.trustStore", trustStorePath);
            env.put("javax.net.ssl.trustStorePassword", ldapProperties.getTlsTrustStorePassword());
            env.put("javax.net.ssl.trustStoreType", ldapProperties.getTlsTrustStoreType());
        }
        return env;
    }

    /**
     * Closes an LDAP context.
     */
    private void closeContext(DirContext ctx) {
        if (ctx != null) {
            try {
                ctx.close();
            } catch (NamingException e) {
                // Ignore
            }
        }
    }

    /**
     * Safely extracts host:port from LDAP URL for logging, avoiding credential exposure.
     * Handles formats like: ldap://host:389, ldap://user:pass@host:389, ldaps://host
     */
    public static String safeLogHost(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            String withoutProtocol = url.replaceFirst("^ldaps?://", "");
            int atIndex = withoutProtocol.indexOf('@');
            if (atIndex > 0) {
                withoutProtocol = withoutProtocol.substring(atIndex + 1);
            }
            // IPv6 literal: ldap://[::1]:389 — keep the bracketed address plus port
            int bracketEnd = withoutProtocol.indexOf(']');
            if (bracketEnd > 0) {
                return withoutProtocol.substring(0, Math.min(bracketEnd + 1, withoutProtocol.length()));
            }
            int slashIndex = withoutProtocol.indexOf('/');
            String hostPort = slashIndex > 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
            return hostPort;
        } catch (Exception e) {
            return "[url-parse-error]";
        }
    }

    /**
     * Returns whether the throwable chain indicates a TLS/trust failure (LDAPS handshake or
     * certificate validation). JNDI wraps TLS failures in a {@link CommunicationException}, so
     * without this check a certificate problem is indistinguishable from an unreachable directory.
     */
    static boolean isTlsFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SSLException || c instanceof CertificateException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Authenticates a user against the LDAP server using their DN and password.
     */
    private boolean authenticateLdap(String userDn, String password) {
        // Reject null/empty passwords explicitly: a null value would otherwise reach
        // Hashtable.put (NPE -> 500), and an empty password could be accepted by directories
        // that allow anonymous/weak binds. Treat both as credential failures (401).
        if (password == null || password.isEmpty()) {
            log.debug("LDAP bind rejected: empty password for DN {}", userDn);
            return false;
        }
        DirContext ctx = null;
        try {
            // Bind as the authenticating user to verify credentials (shared factory handles env).
            ctx = createLdapContext(userDn, password);
            return true;
        } catch (AuthenticationException e) {
            // Invalid credentials — expected, return false to signal auth failure
            return false;
        } catch (CommunicationException e) {
            if (isTlsFailure(e)) {
                log.warn("LDAP TLS/certificate failure while authenticating DN {}: {}", userDn, e.getMessage());
                throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.tlsError");
            }
            log.warn("LDAP directory unavailable while authenticating DN {}: {}", userDn, e.getMessage());
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.directoryUnavailable");
        } catch (NamingException e) {
            log.warn("LDAP naming error while authenticating DN {}: {}", userDn, e.getMessage());
            return false;
        } finally {
            closeContext(ctx);
        }
    }

    /**
     * Retrieves user attributes from LDAP.
     */
    private Attributes getUserAttributes(String userDn) {
        DirContext ctx = null;
        try {
            // Read attributes via the bind-account context (shared factory handles env/timeout).
            ctx = createLdapContext();
            Attributes attrs;
            try {
                // Request user attributes ("*") and operational attributes ("+") so stable
                // directory identifiers (OpenLDAP entryUUID, AD objectGUID) are included in the
                // response. Without the explicit request, operational attributes are omitted and
                // the subject key would be null on every login.
                attrs = ctx.getAttributes(new LdapName(userDn), new String[]{"*", "+"});
            } catch (Exception e) {
                // Some directories reject the "*"/"+" attribute-request syntax; fall back to the
                // default attribute set instead of failing the whole login.
                attrs = ctx.getAttributes(new LdapName(userDn));
            }
            return attrs;
        } catch (CommunicationException e) {
            if (isTlsFailure(e)) {
                log.warn("LDAP TLS/certificate failure while fetching attributes for DN {}: {}", userDn, e.getMessage());
                throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.tlsError");
            }
            log.warn("LDAP directory unavailable while fetching attributes for DN {}: {}", userDn, e.getMessage());
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.directoryUnavailable");
        } catch (Exception e) {
            log.warn("Failed to fetch user attributes from LDAP for DN {}: {}", userDn, e.getMessage());
            return null;
        } finally {
            closeContext(ctx);
        }
    }

    /**
     * Finds an existing LDAP user or creates a new one based on LDAP attributes.
     * <p>
     * Identity is anchored on the stable LDAP subject attribute (entryUUID/objectGUID)
     * via {@link IdentityBinding}, not on the user's email. This prevents:
     * <ul>
     *   <li>Silent account merging when an LDAP email collides with a local/OAuth account</li>
     *   <li>Duplicate accounts for email-less LDAP users on repeated logins</li>
     * </ul>
     */
    UserAccount findOrCreateLdapUser(String username, Attributes attributes) {
        String subject = getAttributeValue(attributes, ldapProperties.getSubjectAttribute());
        String email = getAttributeValue(attributes, ldapProperties.getEmailAttribute());
        String displayName = resolveDisplayName(attributes, username);

        if (subject == null || subject.isEmpty()) {
            log.error("LDAP entry for {} has no stable subject attribute '{}'; cannot bind identity",
                username, ldapProperties.getSubjectAttribute());
            // Bind already succeeded. The directory entry lacks the configured subject attribute,
            // which is a configuration/schema issue the user cannot fix. Surface a 503 with the
            // fetchUserFailed message (no "retry later" wording) instead of a 401 that would look
            // like a wrong password. Operators can locate the cause via the log line above.
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.fetchUserFailed");
        }

        // Anchor on the stable LDAP subject: an existing binding means this identity is already known.
        IdentityBinding binding = identityBindingRepository
            .findByProviderCodeAndSubject(LDAP_PROVIDER, subject)
            .orElse(null);

        if (binding != null) {
            // Returning user — refresh attributes from the directory on each login.
            UserAccount user = userAccountRepository.findById(binding.getUserId())
                .orElseThrow(() -> new IllegalStateException("User not found for LDAP binding " + subject));
            updateFromAttributes(user, displayName, email);
            return userAccountRepository.save(user);
        }

        // First login for this LDAP identity. Refuse to silently inherit an existing local/OAuth
        // account that happens to share the same email — that would be a privilege escalation.
        if (email != null && !email.isEmpty()) {
            String normalizedEmail = email.toLowerCase();
            UserAccount existingByEmail = userAccountRepository
                .findByEmailIgnoreCase(normalizedEmail).orElse(null);
            if (existingByEmail != null) {
                // The email already belongs to another account. Refuse to silently create a second
                // account (two distinct LDAP subjects sharing one email would both map to it, and
                // user_account.email has no UNIQUE constraint, so this would otherwise happen
                // silently). This covers both cross-provider collisions and the same-issuer case
                // (a different LDAP subject under the same email). If an entry's stable subject
                // legitimately changes (e.g. after an AD objectGUID migration), an administrator
                // must remove the stale binding before the new subject can log in.
                log.warn("LDAP user {} email {} collides with an existing account {} (subject differs); refusing to create a duplicate account",
                    username, normalizedEmail, existingByEmail.getId());
                throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.ldap.emailConflict");
            }
        }

        // Create a new user account. The placeholder email is only used to satisfy the NOT NULL
        // constraint and never serves as an identity key.
        String normalizedEmail = email != null && !email.isEmpty()
            ? email.toLowerCase()
            : LDAP_PROVIDER + ":" + username + "@internal";

        UserAccount user = new UserAccount(
            "usr_" + UUID.randomUUID(),
            displayName,
            normalizedEmail,
            null
        );
        user.setStatus(UserStatus.ACTIVE);
        user = userAccountRepository.save(user);
        globalNamespaceMembershipService.ensureMember(user.getId());

        try {
            IdentityBinding newBinding = new IdentityBinding(user.getId(), LDAP_PROVIDER, subject, username);
            // saveAndFlush surfaces the unique (provider_code, subject) constraint immediately, so
            // a concurrent first login for the same subject is detected here and the whole
            // sub-transaction (account + membership + binding) is rolled back.
            identityBindingRepository.saveAndFlush(newBinding);
        } catch (DataIntegrityViolationException e) {
            // A concurrent first login for the same subject committed its binding first. Clear the
            // failed persist state (otherwise Hibernate throws AssertionFailure while rolling back
            // the session), then signal the race so the identity is re-resolved in a fresh
            // transaction. The insert failure already marked this sub-transaction rollback-only,
            // so it can never be committed with the re-resolved state.
            entityManager.clear();
            throw new LdapBindingRaceException(e);
        }

        return user;
    }

    /**
     * Re-resolves a returning LDAP user in a fresh transaction after a concurrent first-login
     * race. Called only when the racing transaction has committed its binding, so the lookup is
     * guaranteed to hit the existing account.
     */
    UserAccount resolveReturningUser(Attributes attributes, String username) {
        String subject = getAttributeValue(attributes, ldapProperties.getSubjectAttribute());
        String email = getAttributeValue(attributes, ldapProperties.getEmailAttribute());
        String displayName = resolveDisplayName(attributes, username);
        IdentityBinding binding = identityBindingRepository
            .findByProviderCodeAndSubject(LDAP_PROVIDER, subject)
            .orElseThrow(() -> new IllegalStateException("No LDAP binding found after race for subject " + subject));
        UserAccount user = userAccountRepository.findById(binding.getUserId())
            .orElseThrow(() -> new IllegalStateException("User not found for LDAP binding " + subject));
        updateFromAttributes(user, displayName, email);
        return userAccountRepository.save(user);
    }

    private String resolveDisplayName(Attributes attributes, String username) {
        String displayName = getAttributeValue(attributes, ldapProperties.getDisplayNameAttribute());
        if (displayName == null || displayName.isEmpty()) {
            displayName = getAttributeValue(attributes, ldapProperties.getDisplayNameFallbackAttribute());
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = username;
        }
        return displayName;
    }

    private void updateFromAttributes(UserAccount user, String displayName, String email) {
        if (displayName != null && !displayName.isEmpty()) {
            user.setDisplayName(displayName);
        }
        if (email != null && !email.isEmpty()) {
            String normalizedEmail = email.toLowerCase(Locale.ROOT);
            // A bound user may update their own email, but must never silently adopt an email
            // that already belongs to a different account (same rule as first login).
            if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(normalizedEmail)) {
                UserAccount existingByEmail = userAccountRepository
                    .findByEmailIgnoreCase(normalizedEmail).orElse(null);
                if (existingByEmail != null && !existingByEmail.getId().equals(user.getId())) {
                    log.warn("LDAP user {} email {} collides with another account {} on refresh; refusing to update",
                        user.getDisplayName(), normalizedEmail, existingByEmail.getId());
                    throw new AuthFlowException(HttpStatus.CONFLICT, "error.auth.ldap.emailConflict");
                }
            }
            user.setEmail(normalizedEmail);
        }
    }

    /**
     * Gets a string attribute value from LDAP attributes.
     */
    private String getAttributeValue(Attributes attributes, String attrName) {
        try {
            Attribute attr = attributes.get(attrName);
            if (attr != null && attr.get() != null) {
                Object value = attr.get();
                // Active Directory stores stable identifiers such as objectGUID / objectSid as
                // binary (OctetString). JNDI returns these as byte[], whose toString() yields an
                // unstable "[B@<identityHashCode>" — making every login look like a new identity.
                // Convert binary values to a stable hexadecimal representation (mixed-endian GUID
                // layout for 16-byte values) so the subject key remains stable across logins.
                if (value instanceof byte[] bytes) {
                    return toStableGuidString(bytes);
                }
                return value.toString();
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }

    /**
     * Converts a binary attribute value into a stable string suitable for use as an identity
     * subject. Active Directory objectGUID is a 16-byte mixed-endian GUID; rearranging it into
     * the canonical 8-4-4-4-12 hex layout yields the same string .NET/AD display, which is stable
     * across JVM restarts and connections. Non-16-byte binaries fall back to plain hex so the
     * value is still deterministic.
     */
    private static String toStableGuidString(byte[] bytes) {
        if (bytes.length == 16) {
            // AD objectGUID layout: little-endian uint32, little-endian uint16 x2, big-endian rest.
            return String.format("%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                bytes[3] & 0xff, bytes[2] & 0xff, bytes[1] & 0xff, bytes[0] & 0xff,
                bytes[5] & 0xff, bytes[4] & 0xff,
                bytes[7] & 0xff, bytes[6] & 0xff,
                bytes[8] & 0xff, bytes[9] & 0xff,
                bytes[10] & 0xff, bytes[11] & 0xff, bytes[12] & 0xff,
                bytes[13] & 0xff, bytes[14] & 0xff, bytes[15] & 0xff);
        }
        // Non-GUID binary attribute: deterministic plain hex so the value stays stable.
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    /**
     * Builds a PlatformPrincipal from a UserAccount.
     */
    private PlatformPrincipal buildPrincipal(UserAccount user) {
        Set<String> roles = userRoleBindingRepository.findByUserId(user.getId()).stream()
            .map(binding -> binding.getRole().getCode())
            .collect(Collectors.toSet());
        roles = PlatformRoleDefaults.withDefaultUserRole(roles);
        return new PlatformPrincipal(
            user.getId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getAvatarUrl(),
            "ldap",
            roles
        );
    }

    /**
     * Validates username to prevent LDAP injection attacks.
     * Allows alphanumeric, underscore, hyphen, dot, and @ (for UPN formats),
     * 3-64 characters.
     */
    private boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        return USERNAME_PATTERN.matcher(username).matches();
    }

    /**
     * Validates all operator-configured LDAP attribute names that flow into JNDI calls.
     * Rejects names that are null or fail the LDAP attribute-name pattern, preventing
     * filter/attribute injection via misconfiguration before any directory call is made.
     */
    private void validateAttributeNames() {
        String[] attrNames = {
            ldapProperties.getUserSearchAttribute(),
            ldapProperties.getSubjectAttribute(),
            ldapProperties.getDisplayNameAttribute(),
            ldapProperties.getDisplayNameFallbackAttribute(),
            ldapProperties.getEmailAttribute()
        };
        for (String name : attrNames) {
            if (name == null || !ATTRIBUTE_NAME_PATTERN.matcher(name).matches()) {
                log.error("Invalid LDAP attribute name configured: {}", name);
                throw new AuthFlowException(HttpStatus.INTERNAL_SERVER_ERROR, "error.auth.ldap.fetchUserFailed");
            }
        }
    }
}
