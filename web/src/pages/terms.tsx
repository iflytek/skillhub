import { useTranslation } from 'react-i18next'
import { LegalDocument } from '@/shared/components/legal-document'

const termsDocuments = {
  zh: {
    eyebrow: '法律与政策',
    title: '服务条款',
    summary: '本条款适用于你访问和使用 SkillHub 提供的技能浏览、发布、审核、下载、账号管理和 API 相关服务。使用本服务即表示你同意受本条款约束。',
    lastUpdated: '最后更新：2026年3月14日',
    note: '如果你所在组织使用的是私有部署版 SkillHub，该组织还可以在本条款之外增加自己的内部规范、信息安全要求或可接受使用政策。',
    sections: [
      {
        title: '1. 接受条款与适用范围',
        paragraphs: [
          '当你访问、注册或使用 SkillHub 时，即表示你同意本服务条款以及与之相关的隐私政策和实例运营规则。',
          '如果你代表团队、公司或其他组织使用 SkillHub，你确认自己有权代表该主体接受这些条款。',
        ],
      },
      {
        title: '2. 账号与访问安全',
        paragraphs: [],
        bullets: [
          '你应提供真实、完整且最新的注册或登录信息，并妥善保护自己的账号、密码、OAuth 会话和 API Token。',
          '你需对通过自己账号发生的活动负责，包括发布、审核、下载、生成令牌和管理命名空间的操作。',
          '如果你发现未授权访问、凭据泄露或其他安全事件，应及时通知实例管理员或运营方。',
        ],
      },
      {
        title: '3. 技能、命名空间与用户内容',
        paragraphs: [
          '你保留对自己上传或提交内容的权利，但你授予 SkillHub 一项非独占、全球性、可再许可的许可，以便在提供服务所必需的范围内托管、存储、复制、处理、展示和分发这些内容。',
          '你承诺对自己提交的技能包、README、描述、截图、审核意见和其他材料负责，并保证你有权上传这些内容。',
        ],
        bullets: [
          '不得上传恶意代码、违法内容、侵权内容、欺诈性内容或故意误导用户的材料。',
          '不得冒用他人身份、未经授权占用命名空间，或规避平台的审核与访问控制机制。',
          '下载和再分发技能时，你还必须遵守该技能自身的许可证、第三方依赖许可证和适用法律。',
        ],
      },
      {
        title: '4. 审核、治理与执行',
        paragraphs: [
          'SkillHub 可对发布内容执行审核、隐藏、下架、拒绝、撤回版本、限制访问或暂停账号等治理动作，以维护平台安全、合规和服务质量。',
          '当存在滥用、侵权、安全风险、违法行为或违反本条款的情况时，平台或实例管理员可保留相关日志并采取必要处置。',
        ],
      },
      {
        title: '5. 下载、API 与可接受使用',
        paragraphs: [],
        bullets: [
          '你不得干扰服务稳定性，不得绕过鉴权、限流、安全策略或未获授权访问他人资源。',
          '你不得以破坏性方式抓取、压测、扫描或自动化调用 SkillHub，也不得借助平台传播病毒、木马或其他恶意负载。',
          '通过 SkillHub 下载的技能由提供者负责，并按其各自的许可证和风险提示使用；你应自行评估兼容性、安全性和合规性。',
        ],
      },
      {
        title: '6. 开源组件、知识产权与品牌',
        paragraphs: [
          'SkillHub 可能包含受 Apache License 2.0 或其他第三方许可证约束的开源组件，这些组件仍分别受其自身许可证管理。',
          '除非明确说明，SkillHub 的商标、品牌元素、界面设计和站点内容仍归相应权利人所有，不因你使用服务而转让。',
        ],
      },
      {
        title: '7. 服务可用性与免责声明',
        paragraphs: [
          '我们可以随时调整、更新、限制或中断部分功能，包括搜索、下载、审核、登录方式和 API 能力，且不保证任何功能持续可用。',
          '在适用法律允许的范围内，SkillHub 按“现状”和“可用”提供，不对适销性、特定用途适用性、不中断、无错误或内容准确性作出明示或默示保证。',
        ],
      },
      {
        title: '8. 责任限制',
        paragraphs: [
          '在适用法律允许的最大范围内，SkillHub 及其运营方不对任何间接、附带、特殊、后果性损害或利润、数据、商誉损失承担责任。',
          '如果你的使用、内容或违规行为导致第三方向 SkillHub 或实例运营方提出索赔，你同意在法律允许范围内承担相应责任并配合处理。',
        ],
      },
      {
        title: '9. 终止、变更与联系',
        paragraphs: [
          '你可以随时停止使用 SkillHub。我们也可以在你违反条款、造成安全风险或法律要求时暂停或终止你对服务的访问。',
          '我们可能更新本条款。更新后继续使用服务即表示你接受修订版本。如需联系，请使用当前实例提供的文档、社区或管理员渠道。',
        ],
      },
    ],
  },
  en: {
    eyebrow: 'Legal',
    title: 'Terms of Service',
    summary: 'These terms apply to your access to and use of SkillHub for browsing, publishing, reviewing, downloading, account management, and related API services. By using the service, you agree to these terms.',
    lastUpdated: 'Last updated: March 14, 2026',
    note: 'If your organization runs a private SkillHub deployment, it may impose additional internal rules, information security requirements, or acceptable use policies on top of these baseline terms.',
    sections: [
      {
        title: '1. Acceptance and Scope',
        paragraphs: [
          'By accessing, registering for, or using SkillHub, you agree to these Terms of Service, the related Privacy Policy, and any operating rules for the current instance.',
          'If you use SkillHub on behalf of a team, company, or other organization, you represent that you have authority to accept these terms on its behalf.',
        ],
      },
      {
        title: '2. Accounts and Access Security',
        paragraphs: [],
        bullets: [
          'You must provide accurate and current account information and protect your credentials, OAuth sessions, and API tokens.',
          'You are responsible for activity performed through your account, including publishing, reviewing, downloading, token generation, and namespace administration.',
          'If you become aware of unauthorized access, credential leakage, or another security incident, you must promptly notify the instance administrator or operator.',
        ],
      },
      {
        title: '3. Skills, Namespaces, and User Content',
        paragraphs: [
          'You retain rights in content you upload or submit, but you grant SkillHub a non-exclusive, worldwide, sublicensable license to host, store, copy, process, display, and distribute that content as needed to operate the service.',
          'You are responsible for the skill packages, README files, descriptions, screenshots, review comments, and other materials you submit, and you represent that you have the right to provide them.',
        ],
        bullets: [
          'Do not upload malware, unlawful material, infringing material, deceptive material, or content intended to mislead users.',
          'Do not impersonate others, take namespaces without authorization, or attempt to bypass review, moderation, or access control mechanisms.',
          'When downloading or redistributing a skill, you must also comply with that skill’s own license terms, third-party dependency licenses, and applicable law.',
        ],
      },
      {
        title: '4. Review, Governance, and Enforcement',
        paragraphs: [
          'SkillHub may review content and may approve, reject, hide, remove, yank versions, restrict access, or suspend accounts to protect service security, compliance, and quality.',
          'Where abuse, infringement, security risk, unlawful conduct, or other violations are suspected, the platform or instance administrator may preserve logs and take appropriate action.',
        ],
      },
      {
        title: '5. Downloads, APIs, and Acceptable Use',
        paragraphs: [],
        bullets: [
          'You may not interfere with service stability or bypass authentication, rate limits, security controls, or authorization boundaries.',
          'You may not scrape, stress test, scan, or automate against SkillHub in a destructive manner, or use the service to distribute viruses, trojans, or other malicious payloads.',
          'Skills downloaded through SkillHub are provided by their publishers and are used subject to their own licenses and risk notices. You are responsible for evaluating compatibility, security, and compliance.',
        ],
      },
      {
        title: '6. Open Source Components, Intellectual Property, and Branding',
        paragraphs: [
          'SkillHub may include open-source components governed by Apache License 2.0 or other third-party licenses, and those components remain subject to their respective license terms.',
          'Unless explicitly stated otherwise, SkillHub trademarks, brand assets, interface design, and site content remain the property of their respective owners and are not transferred by your use of the service.',
        ],
      },
      {
        title: '7. Availability and Disclaimers',
        paragraphs: [
          'We may modify, update, limit, or discontinue parts of the service at any time, including search, downloads, review workflows, login methods, and API capabilities, and we do not guarantee continuous availability.',
          'To the maximum extent permitted by law, SkillHub is provided on an "as is" and "as available" basis without express or implied warranties, including warranties of merchantability, fitness for a particular purpose, non-infringement, uninterrupted availability, or accuracy.',
        ],
      },
      {
        title: '8. Limitation of Liability',
        paragraphs: [
          'To the maximum extent permitted by law, SkillHub and its operators will not be liable for indirect, incidental, special, consequential, or punitive damages, or for any loss of profits, data, or goodwill.',
          'If claims arise from your use of the service, your content, or your violation of these terms, you agree to bear responsibility as permitted by law and to cooperate in resolving the matter.',
        ],
      },
      {
        title: '9. Termination, Changes, and Contact',
        paragraphs: [
          'You may stop using SkillHub at any time. We may suspend or terminate access if you violate these terms, create security risk, or if required by law.',
          'We may update these terms from time to time. Your continued use after an update means you accept the revised version. If you need to contact us, use the documentation, community, or administrator channel provided by the current instance.',
        ],
      },
    ],
  },
} as const

export function TermsOfServicePage() {
  const { i18n } = useTranslation()
  const language = i18n.resolvedLanguage?.split('-')[0] === 'zh' ? 'zh' : 'en'

  return <LegalDocument {...termsDocuments[language]} />
}
