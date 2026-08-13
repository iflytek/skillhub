package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.setting.SystemSettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Gives each newly activated account a namespace of its own, when the operator has asked for it.
 *
 * <p>The namespace is an ordinary team namespace whose only member is its owner, which is what
 * "private" means in this model: there is no namespace-level visibility flag, and skill visibility
 * stays a property of each skill.
 *
 * <p>Provisioning deliberately runs in its own transaction, after the account has been committed.
 * {@code namespace.created_by} and {@code namespace_member.user_id} both reference
 * {@code user_account(id)}, so creating the namespace inside the still-open registration
 * transaction would either join that transaction — letting a naming clash roll back the
 * registration — or, if suspended, block on the uncommitted account row. Running afterwards keeps a
 * failure here from costing the user their account; see
 * {@code PersonalNamespaceProvisioningListener}.
 */
@Service
public class PersonalNamespaceProvisioningService {

    public static final String SETTING_KEY = "namespace.personal-provisioning";

    /**
     * Upper bound on de-duplication suffixes before giving up on a slug base.
     */
    private static final int MAX_SLUG_ATTEMPTS = 64;

    private static final Logger log = LoggerFactory.getLogger(PersonalNamespaceProvisioningService.class);

    private final SystemSettingService systemSettingService;
    private final PersonalNamespaceProvisioningProperties defaults;
    private final NamespaceService namespaceService;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;

    public PersonalNamespaceProvisioningService(SystemSettingService systemSettingService,
                                                PersonalNamespaceProvisioningProperties defaults,
                                                NamespaceService namespaceService,
                                                NamespaceRepository namespaceRepository,
                                                NamespaceMemberRepository namespaceMemberRepository) {
        this.systemSettingService = systemSettingService;
        this.defaults = defaults;
        this.namespaceService = namespaceService;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
    }

    /**
     * Returns the effective policy: the administrator's stored choice, or the deployment defaults.
     */
    public PersonalNamespaceSettings currentSettings() {
        return systemSettingService.get(SETTING_KEY, PersonalNamespaceSettings.class, defaults.toSettings());
    }

    @Transactional
    public PersonalNamespaceSettings updateSettings(PersonalNamespaceSettings settings, String actorUserId) {
        return systemSettingService.put(SETTING_KEY, settings, actorUserId);
    }

    /**
     * Creates the owner's namespace, or returns empty when provisioning is off, the owner already
     * has one, or no acceptable slug is available.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Namespace> provisionFor(PersonalNamespaceOwner owner) {
        PersonalNamespaceSettings settings = currentSettings();
        if (!settings.enabled()) {
            return Optional.empty();
        }
        if (alreadyOwnsNamespace(owner.userId())) {
            return Optional.empty();
        }

        String slug = allocateSlug(settings.slugTemplate(), owner);
        if (slug == null) {
            log.warn("No namespace slug available for user {} from template '{}'; skipping provisioning",
                    owner.userId(), settings.slugTemplate());
            return Optional.empty();
        }

        String displayName = PersonalNamespaceNaming.displayName(settings.displayNameTemplate(), owner, slug);
        Namespace namespace = namespaceService.createNamespace(slug, displayName, null, owner.userId());
        log.info("Provisioned personal namespace '{}' for user {}", slug, owner.userId());
        return Optional.of(namespace);
    }

    /**
     * Treats owning any non-global namespace as "already has a personal namespace", which keeps a
     * repeated activation from handing the same user a second one.
     */
    private boolean alreadyOwnsNamespace(String userId) {
        return namespaceMemberRepository.findByUserId(userId).stream()
                .filter(member -> member.getRole() == NamespaceRole.OWNER)
                .map(member -> namespaceRepository.findById(member.getNamespaceId()))
                .flatMap(Optional::stream)
                .anyMatch(namespace -> namespace.getType() != NamespaceType.GLOBAL);
    }

    /**
     * Returns the first free slug for the owner, or {@code null} when every candidate is taken or
     * rejected — for example when the template renders to a reserved word for many users.
     */
    private String allocateSlug(String slugTemplate, PersonalNamespaceOwner owner) {
        String base = PersonalNamespaceNaming.slugBase(slugTemplate, owner);
        for (int attempt = 1; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            String candidate = attempt == 1 ? base : base + "-" + attempt;
            if (SlugValidator.isValid(candidate) && namespaceRepository.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }
}
