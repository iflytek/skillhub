import type { SitePresentationSettings } from '@/api/site-presentation'
import { withBasePath } from './base-path'

export const PRESENTATION_CONTENT = {
  "hero": {
    "badge": "landing.experience.heroBadge",
    "title": "landing.experience.heroTitle",
    "description": "landing.experience.heroDescription",
    "primaryActionLabel": "landing.hero.publishSkill",
    "secondaryActionLabel": "landing.hero.exploreSkills",
    "searchPlaceholder": "landing.hero.searchPlaceholder",
    "previewTitle": "=Skill Registry",
    "previewCaption": "landing.experience.demoSkills.caption",
    "previewPublishTitle": "landing.hero.publishSkill",
    "previewPublishDescription": "=Web · CLI · Agent",
    "highlightSelfHosted": "landing.experience.highlights.selfHosted",
    "highlightGovernance": "landing.experience.highlights.governance",
    "highlightMultiClient": "landing.experience.highlights.multiClient",
    "highlightTraceable": "landing.experience.highlights.traceable"
  },
  "curated": {},
  "capabilities": {
    "eyebrow": "landing.experience.capabilities.eyebrow",
    "title": "landing.experience.capabilities.title",
    "description": "landing.experience.capabilities.description",
    "secureTitle": "landing.features.secure.title",
    "secureDescription": "landing.features.secure.description",
    "communityTitle": "landing.features.community.title",
    "communityDescription": "landing.features.community.description",
    "integrationTitle": "landing.features.integration.title",
    "integrationDescription": "landing.features.integration.description",
    "versionControlTitle": "landing.features.versionControl.title",
    "versionControlDescription": "landing.features.versionControl.description",
    "cliTitle": "landing.features.cli.title",
    "cliDescription": "landing.features.cli.description",
    "governanceTitle": "landing.features.governance.title",
    "governanceDescription": "landing.features.governance.description"
  },
  "quickStart": {
    "title": "landing.experience.quickStart.title",
    "description": "landing.experience.quickStart.description",
    "agentTitle": "landing.experience.quickStart.modes.agent.title",
    "agentDescription": "landing.experience.quickStart.modes.agent.description",
    "cliTitle": "landing.experience.quickStart.modes.cli.title",
    "cliDescription": "landing.experience.quickStart.modes.cli.description",
    "webTitle": "landing.experience.quickStart.modes.web.title",
    "webDescription": "landing.experience.quickStart.modes.web.description"
  },
  "popular": {
    "eyebrow": "landing.experience.marketplace",
    "title": "home.popularTitle",
    "description": "home.popularDescription",
    "viewAllLabel": "home.viewAll"
  },
  "latest": {
    "eyebrow": "landing.experience.latest",
    "title": "home.latestTitle",
    "description": "home.latestDescription",
    "viewAllLabel": "home.viewAll"
  },
  "enterprise": {
    "eyebrow": "=Enterprise",
    "title": "landing.experience.enterprise.title",
    "description": "landing.experience.enterprise.description",
    "deploymentTitle": "landing.experience.enterprise.deployment.title",
    "deploymentDescription": "landing.experience.enterprise.deployment.description",
    "securityTitle": "landing.experience.enterprise.security.title",
    "securityDescription": "landing.experience.enterprise.security.description",
    "rbacTitle": "landing.experience.enterprise.rbac.title",
    "rbacDescription": "landing.experience.enterprise.rbac.description",
    "accessTitle": "landing.experience.enterprise.access.title",
    "accessDescription": "landing.experience.enterprise.access.description",
    "auditTitle": "landing.experience.enterprise.audit.title",
    "auditDescription": "landing.experience.enterprise.audit.description",
    "openSourceTitle": "landing.experience.enterprise.openSource.title",
    "openSourceDescription": "landing.experience.enterprise.openSource.description",
    "clientLayer": "landing.experience.enterprise.clientLayer",
    "serviceLayer": "landing.experience.enterprise.serviceLayer",
    "storageLayer": "landing.experience.enterprise.storageLayer",
    "networkBoundary": "landing.experience.enterprise.networkBoundary",
    "registryLabel": "=SkillHub\nRegistry"
  },
  "cta": {
    "title": "landing.experience.cta.title",
    "description": "landing.experience.cta.description",
    "primaryActionLabel": "landing.experience.cta.deploy",
    "secondaryActionLabel": "landing.experience.cta.docs"
  },
  "footer": {
    "description": "layout.footerDescription",
    "copyright": "footer.copyright",
    "productTitle": "footer.product",
    "developersTitle": "footer.developers",
    "projectTitle": "footer.project",
    "resourcesTitle": "footer.resources",
    "marketLabel": "footer.marketplace",
    "publishLabel": "footer.publish",
    "consoleLabel": "nav.dashboard",
    "documentationLabel": "footer.docs",
    "cliPackageLabel": "=CLI",
    "repositoryLabel": "=GitHub",
    "licenseLabel": "footer.license",
    "contributingLabel": "footer.contributing",
    "changelogLabel": "footer.changelog",
    "codeOfConductLabel": "footer.codeOfConduct",
    "communityLabel": "footer.community",
    "privacyLabel": "footer.privacy",
    "termsLabel": "footer.terms"
  }
} as const

export type PresentationSection = keyof typeof PRESENTATION_CONTENT
export const PRESENTATION_ITEMS = {
  "hero": [
    "preview",
    "highlights",
    "primaryAction",
    "secondaryAction"
  ],
  "curated": [],
  "capabilities": [
    "secure",
    "community",
    "integration",
    "versionControl",
    "cli",
    "governance"
  ],
  "quickStart": [
    "agent",
    "cli",
    "web"
  ],
  "popular": [],
  "latest": [],
  "enterprise": [
    "deployment",
    "security",
    "rbac",
    "access",
    "audit",
    "openSource"
  ],
  "cta": [
    "primaryAction",
    "secondaryAction"
  ],
  "footer": [
    "product",
    "developers",
    "project",
    "resources"
  ]
} as const
export const PRESENTATION_LINKS = {
  "repository": "https://github.com/iflytek/skillhub",
  "documentation": "https://iflytek.github.io/skillhub/",
  "cliDocumentation": "https://github.com/iflytek/skillhub/tree/main/cli",
  "cliPackage": "https://www.npmjs.com/package/@astron-team/skillhub",
  "license": "https://github.com/iflytek/skillhub/blob/main/LICENSE",
  "contributing": "https://github.com/iflytek/skillhub/blob/main/CONTRIBUTING.md",
  "changelog": "https://github.com/iflytek/skillhub/releases",
  "codeOfConduct": "https://github.com/iflytek/skillhub/blob/main/CODE_OF_CONDUCT.md",
  "community": "https://github.com/iflytek/skillhub/discussions"
} as const
export type PresentationLink = keyof typeof PRESENTATION_LINKS

export function presentationItemVisible(settings: SitePresentationSettings, section: PresentationSection, item: string): boolean {
  return settings.sections[section].items?.[item] !== false
}

const hasUnsafeUrlCharacters = (value: string) => Array.from(value).some(character => character === '\\' || character.charCodeAt(0) <= 32 || character.charCodeAt(0) === 127)

/** Root-relative links belong to this frontend, including deployments under a base path. */
export function presentationLinkUrl(settings: SitePresentationSettings, slot: PresentationLink, fallback: string = PRESENTATION_LINKS[slot]): string {
  const value = settings.links[slot]?.trim()
  if (value?.startsWith('/')) {
    try {
      let decodedPath = value.split(/[?#]/)[0]
      for (let pass = 0; pass < 5; pass += 1) {
        let decoded: string
        try { decoded = decodeURIComponent(decodedPath) } catch { if (pass === 0) return fallback; break }
        if (decoded === decodedPath) break
        decodedPath = decoded
      }
      if (/%(?:25|2e|2f|5c)/i.test(decodedPath)) return fallback
      if (!decodedPath.startsWith('//') && !hasUnsafeUrlCharacters(decodedPath) && !/(^|\/)\.{1,2}(\/|$)/.test(decodedPath) && !hasUnsafeUrlCharacters(value)) return withBasePath(value)
    } catch { /* Invalid encodings must not bypass the base path boundary. */ }
    return fallback
  }
  if (value && !hasUnsafeUrlCharacters(value)) {
    try { const url = new URL(value); if (['https:', 'http:'].includes(url.protocol) && !url.username && !url.password) return url.href } catch { /* Retain the product default for invalid settings. */ }
  }
  return fallback
}
