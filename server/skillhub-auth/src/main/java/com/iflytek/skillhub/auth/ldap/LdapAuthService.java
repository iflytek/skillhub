package com.iflytek.skillhub.auth.ldap;

import com.iflytek.skillhub.auth.config.LdapProperties;
import com.iflytek.skillhub.auth.exception.AuthFlowException;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.auth.rbac.PlatformRoleDefaults;
import com.iflytek.skillhub.auth.repository.UserRoleBindingRepository;
import com.iflytek.skillhub.domain.namespace.GlobalNamespaceMembershipService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles LDAP authentication for enterprise directory integration.
 */
@Service
public class LdapAuthService {

    private static final Logger log = LoggerFactory.getLogger(LdapAuthService.class);

    private final LdapProperties ldapProperties;
    private final LdapTemplate ldapTemplate;
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingRepository userRoleBindingRepository;
    private final GlobalNamespaceMembershipService globalNamespaceMembershipService;

    public LdapAuthService(LdapProperties ldapProperties,
                           LdapTemplate ldapTemplate,
                           UserAccountRepository userAccountRepository,
                           UserRoleBindingRepository userRoleBindingRepository,
                           GlobalNamespaceMembershipService globalNamespaceMembershipService) {
        this.ldapProperties = ldapProperties;
        this.ldapTemplate = ldapTemplate;
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingRepository = userRoleBindingRepository;
        this.globalNamespaceMembershipService = globalNamespaceMembershipService;
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
    @Transactional
    public PlatformPrincipal login(String username, String password) {
        log.info("Starting LDAP authentication for username: {}", username);
        
        if (!ldapProperties.isEnabled()) {
            log.warn("LDAP authentication is not enabled");
            throw new AuthFlowException(HttpStatus.SERVICE_UNAVAILABLE, "error.auth.ldap.disabled");
        }

        log.debug("LDAP URL: {}, Base: {}, SearchBase: {}, SearchAttr: {}", 
            ldapProperties.getUrl(), 
            ldapProperties.getBase(),
            ldapProperties.getUserSearchBase(),
            ldapProperties.getUserSearchAttribute());

        // First, try to find the user in LDAP and authenticate
        String userDn = findUserDn(username);
        log.debug("LDAP findUserDn result for {}: {}", username, userDn);
        
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
            throw new AuthFlowException(HttpStatus.INTERNAL_SERVER_ERROR, "error.auth.ldap.fetchUserFailed");
        }

        // Find or create local user account
        log.debug("Finding or creating local user account for username: {}", username);
        UserAccount user = findOrCreateLdapUser(username, userAttributes);

        // Check if user can login (status check)
        log.debug("Checking user status for user: {}, status: {}", username, user.getStatus());
        ensureUserCanLogin(user);

        log.info("LDAP authentication successful for username: {}", username);
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
        DirContext ctx = null;
        try {
            ctx = createLdapContext();
            String searchFilter = "(" + ldapProperties.getUserSearchAttribute() + "={0})";
            String searchBase = ldapProperties.getUserSearchBase().isEmpty()
                ? ldapProperties.getBase()
                : ldapProperties.getUserSearchBase() + "," + ldapProperties.getBase();

            SearchControls searchControls = new SearchControls();
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            searchControls.setReturningAttributes(new String[0]);

            javax.naming.NamingEnumeration<SearchResult> results = ctx.search(searchBase, searchFilter, new Object[]{username}, searchControls);
            
            if (results.hasMore()) {
                SearchResult result = results.next();
                return result.getNameInNamespace();
            }
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            closeContext(ctx);
        }
    }

    /**
     * Creates an LDAP context for searching.
     */
    private DirContext createLdapContext() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldapProperties.getUrl());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        
        if (ldapProperties.getUsername() != null && !ldapProperties.getUsername().isEmpty()) {
            env.put(Context.SECURITY_PRINCIPAL, ldapProperties.getUsername());
            env.put(Context.SECURITY_CREDENTIALS, ldapProperties.getPassword());
        }
        
        return new InitialDirContext(env);
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
     * Authenticates a user against the LDAP server using their DN and password.
     */
    private boolean authenticateLdap(String userDn, String password) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, ldapProperties.getUrl());
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, userDn);
            env.put(Context.SECURITY_CREDENTIALS, password);

            DirContext ctx = new InitialDirContext(env);
            ctx.close();
            return true;
        } catch (NamingException e) {
            return false;
        }
    }

    /**
     * Retrieves user attributes from LDAP.
     */
    private Attributes getUserAttributes(String userDn) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, ldapProperties.getUrl());
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            
            // Use bind DN if configured, otherwise anonymous bind
            if (ldapProperties.getUsername() != null && !ldapProperties.getUsername().isEmpty()) {
                env.put(Context.SECURITY_PRINCIPAL, ldapProperties.getUsername());
                env.put(Context.SECURITY_CREDENTIALS, ldapProperties.getPassword());
            }
            
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(new LdapName(userDn));
            ctx.close();
            return attrs;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds an existing LDAP user or creates a new one based on LDAP attributes.
     * Note: This method only finds users by email, as the repository doesn't support displayName lookup.
     */
    private UserAccount findOrCreateLdapUser(String username, Attributes attributes) {
        String email = getAttributeValue(attributes, "mail");
        String displayName = getAttributeValue(attributes, "displayName");
        
        if (displayName == null || displayName.isEmpty()) {
            displayName = getAttributeValue(attributes, "cn");
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = username;
        }

        UserAccount user = null;
        
        // Try to find by email first
        if (email != null && !email.isEmpty()) {
            user = userAccountRepository.findByEmailIgnoreCase(email.toLowerCase()).orElse(null);
        }

        // If not found, create a new user
        if (user == null) {
            String normalizedEmail = email != null ? email.toLowerCase() : null;
            
            user = new UserAccount(
                "usr_" + UUID.randomUUID(),
                displayName,
                normalizedEmail,
                null
            );
            user.setStatus(UserStatus.ACTIVE);
            userAccountRepository.save(user);
            globalNamespaceMembershipService.ensureMember(user.getId());
        }

        return user;
    }

    /**
     * Gets a string attribute value from LDAP attributes.
     */
    private String getAttributeValue(Attributes attributes, String attrName) {
        try {
            Attribute attr = attributes.get(attrName);
            if (attr != null && attr.get() != null) {
                return attr.get().toString();
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
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
}
