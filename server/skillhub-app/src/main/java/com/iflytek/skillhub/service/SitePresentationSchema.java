package com.iflytek.skillhub.service;

import java.util.Map;
import java.util.Set;

/** Editable slots of the existing landing page, not an arbitrary page or translation editor. */
final class SitePresentationSchema {
    static final Set<String> LOCALES = Set.of("en", "zh", "ko", "ru");
    static final Map<String, Set<String>> TEXT_SLOTS = Map.of(
            "hero", Set.of("badge", "title", "description", "primaryActionLabel", "secondaryActionLabel", "searchPlaceholder", "previewTitle", "previewCaption", "previewPublishTitle", "previewPublishDescription", "highlightSelfHosted", "highlightGovernance", "highlightMultiClient", "highlightTraceable"),
            "capabilities", Set.of("eyebrow", "title", "description", "secureTitle", "secureDescription", "communityTitle", "communityDescription", "integrationTitle", "integrationDescription", "versionControlTitle", "versionControlDescription", "cliTitle", "cliDescription", "governanceTitle", "governanceDescription"),
            "quickStart", Set.of("title", "description", "agentTitle", "agentDescription", "cliTitle", "cliDescription", "webTitle", "webDescription"),
            "popular", Set.of("eyebrow", "title", "description", "viewAllLabel"),
            "latest", Set.of("eyebrow", "title", "description", "viewAllLabel"),
            "enterprise", Set.of("eyebrow", "title", "description", "deploymentTitle", "deploymentDescription", "securityTitle", "securityDescription", "rbacTitle", "rbacDescription", "accessTitle", "accessDescription", "auditTitle", "auditDescription", "openSourceTitle", "openSourceDescription", "clientLayer", "serviceLayer", "storageLayer", "registryLabel", "networkBoundary"),
            "cta", Set.of("title", "description", "primaryActionLabel", "secondaryActionLabel"),
            "footer", Set.of("description", "copyright", "productTitle", "developersTitle", "projectTitle", "resourcesTitle", "marketLabel", "publishLabel", "consoleLabel", "documentationLabel", "cliPackageLabel", "repositoryLabel", "licenseLabel", "contributingLabel", "changelogLabel", "codeOfConductLabel", "communityLabel", "privacyLabel", "termsLabel"));
    static final Map<String, Set<String>> ITEM_SLOTS = Map.of(
            "hero", Set.of("preview", "highlights", "primaryAction", "secondaryAction"),
            "capabilities", Set.of("secure", "community", "integration", "versionControl", "cli", "governance"),
            "quickStart", Set.of("agent", "cli", "web"),
            "popular", Set.of(),
            "latest", Set.of(),
            "enterprise", Set.of("deployment", "security", "rbac", "access", "audit", "openSource"),
            "cta", Set.of("primaryAction", "secondaryAction"),
            "footer", Set.of("product", "developers", "project", "resources"));
    static final Set<String> LINK_SLOTS = Set.of("repository", "documentation", "cliDocumentation", "cliPackage", "license", "contributing", "changelog", "codeOfConduct", "community");

    private SitePresentationSchema() {}

    static int textLimit(String slot) {
        return slot.equals("description") || slot.endsWith("Description") || slot.equals("copyright") ? 1000 : 160;
    }
}
