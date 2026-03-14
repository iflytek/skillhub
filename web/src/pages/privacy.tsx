import { useTranslation } from 'react-i18next'
import { LegalDocument } from '@/shared/components/legal-document'

const privacyDocuments = {
  zh: {
    eyebrow: '法律与隐私',
    title: '隐私政策',
    summary: '本政策说明 SkillHub 在提供技能注册、发布、审核、下载、账号登录和相关 API 服务时，如何收集、使用、共享并保护与你有关的信息。',
    lastUpdated: '最后更新：2026年3月14日',
    note: '如果你使用的是某个组织自行部署的 SkillHub 实例，该实例的运营方也可能根据其内部政策处理数据，并对其部署环境中的数据承担独立责任。',
    sections: [
      {
        title: '1. 适用范围',
        paragraphs: [
          '本政策适用于 SkillHub 网站、Web 控制台、公开技能页面、登录流程、设备授权流程以及与这些能力相关的接口和服务。',
          '当你浏览技能、发布版本、参与审核、生成令牌或下载内容时，本政策描述我们如何处理与你相关的信息。',
        ],
      },
      {
        title: '2. 我们收集的信息',
        paragraphs: [],
        bullets: [
          '账户与身份信息，例如用户名、邮箱、头像、OAuth 提供方标识、平台角色和命名空间成员关系。',
          '你主动提交的内容，例如技能包、README、版本说明、命名空间资料、评分、星标和审核意见。',
          '使用与安全信息，例如 IP 地址、浏览器或设备信息、请求日志、下载记录、登录事件、API Token 元数据、错误日志和审计日志。',
        ],
      },
      {
        title: '3. 我们如何使用信息',
        paragraphs: [],
        bullets: [
          '提供登录、会话管理、权限控制、设备授权、账号安全和基础客户支持。',
          '展示公开技能页面，支持搜索、下载、评分、星标、命名空间协作和后台治理流程。',
          '执行审核、限流、风控、故障排查、性能分析和产品改进。',
          '在必要时发送与安全、政策更新或服务可用性相关的重要通知。',
        ],
      },
      {
        title: '4. 公开信息与共享',
        paragraphs: [
          '你发布为公开的技能、版本说明、命名空间名称、公开评分以及部分个人资料信息，可能会向其他用户或访客展示。',
          '除为提供托管、认证、监控、合规支持所必需，或为遵守法律要求、保护平台与用户安全外，我们不会出售你的个人信息。',
          '在私有部署场景中，数据也可能由部署运营方按照其内部治理和合规要求访问、处理或保留。',
        ],
      },
      {
        title: '5. 数据保留',
        paragraphs: [
          '我们会在实现业务目的所需的期限内保留账户资料、技能元数据、审核记录和安全日志，并可能在法律、审计或合规要求下延长保留期限。',
          '被撤销的 API Token 将失效，但相关安全与审计记录可能继续保留。删除或下线技能后，备份、副本或日志中的相关信息可能在合理周期内继续存在。',
        ],
      },
      {
        title: '6. 你的选择与权利',
        paragraphs: [],
        bullets: [
          '你可以更新账户资料、修改密码、撤销或重新创建 API Token。',
          '你可以联系实例管理员申请导出、更正或删除与你有关的信息，但某些记录可能因安全、审计或合规义务而不能立即删除。',
          '对于私有部署实例，你的隐私请求通常应优先提交给该实例的运营方或管理员。',
        ],
      },
      {
        title: '7. 安全措施',
        paragraphs: [
          '我们采取合理的技术和组织措施保护数据，例如访问控制、鉴权、审计、令牌管理和传输安全。',
          '但任何互联网服务都无法保证绝对安全。你应妥善保管账号凭据，并在发现异常登录、泄露或滥用时及时通知实例运营方。',
        ],
      },
      {
        title: '8. 政策更新与联系我们',
        paragraphs: [
          '我们可能随着产品能力、法律要求或运营方式变化更新本政策。新版政策发布后将在站内适当位置展示，并以页面标注的更新时间为准。',
          '如需联系，请通过当前实例提供的文档、社区、支持渠道或管理员联系方式与 SkillHub 运营方联系。',
        ],
      },
    ],
  },
  en: {
    eyebrow: 'Legal',
    title: 'Privacy Policy',
    summary: 'This policy explains how SkillHub collects, uses, shares, and protects information when we provide skill registry, publishing, review, download, account, and related API services.',
    lastUpdated: 'Last updated: March 14, 2026',
    note: 'If you use a privately deployed SkillHub instance, that deployment operator may also process data under its own internal policies and may act independently for data handled in that environment.',
    sections: [
      {
        title: '1. Scope',
        paragraphs: [
          'This policy applies to the SkillHub website, web console, public skill pages, login flows, device authorization flows, and related APIs and services.',
          'When you browse skills, publish versions, participate in reviews, create tokens, or download content, this policy describes how we handle information related to you.',
        ],
      },
      {
        title: '2. Information We Collect',
        paragraphs: [],
        bullets: [
          'Account and identity information such as username, email, avatar, OAuth provider identifiers, platform roles, and namespace membership.',
          'Content you submit, including skill packages, README files, release notes, namespace profiles, ratings, stars, and review comments.',
          'Usage and security information such as IP address, browser or device details, request logs, download activity, login events, API token metadata, error logs, and audit logs.',
        ],
      },
      {
        title: '3. How We Use Information',
        paragraphs: [],
        bullets: [
          'To provide login, session management, access control, device authorization, account security, and basic support.',
          'To display public skill pages and power search, downloads, ratings, stars, namespace collaboration, and governance workflows.',
          'To perform review operations, rate limiting, abuse prevention, debugging, performance analysis, and service improvement.',
          'To send important notices related to security, policy changes, or service availability when needed.',
        ],
      },
      {
        title: '4. Public Information and Sharing',
        paragraphs: [
          'Skills you publish publicly, release notes, namespace names, public ratings, and some profile information may be visible to other users or visitors.',
          'We do not sell your personal information. We may share information when necessary to provide hosting, authentication, monitoring, or compliance support, or to comply with law and protect the service and its users.',
          'For private deployments, the instance operator may also access, process, or retain data according to its own internal governance and compliance requirements.',
        ],
      },
      {
        title: '5. Data Retention',
        paragraphs: [
          'We retain account information, skill metadata, review records, and security logs for as long as needed to operate the service and may retain them longer where required for legal, audit, or compliance purposes.',
          'Revoked API tokens stop working, but related security and audit records may remain. After a skill is deleted or hidden, residual copies in backups or logs may persist for a reasonable period.',
        ],
      },
      {
        title: '6. Your Choices and Rights',
        paragraphs: [],
        bullets: [
          'You can update account information, change your password, and revoke or recreate API tokens.',
          'You can contact the instance administrator to request export, correction, or deletion of information related to you, although some records may need to be retained for security, audit, or compliance reasons.',
          'For privately deployed instances, privacy requests should usually be directed to that instance operator first.',
        ],
      },
      {
        title: '7. Security Measures',
        paragraphs: [
          'We use reasonable technical and organizational safeguards such as access controls, authentication, auditing, token management, and transport security.',
          'No internet service can guarantee absolute security. You should protect your credentials and report suspicious access, leakage, or misuse promptly to the instance operator.',
        ],
      },
      {
        title: '8. Policy Updates and Contact',
        paragraphs: [
          'We may update this policy as the product, legal requirements, or operating model changes. The latest version will be posted in the service and identified by its update date.',
          'If you need to contact us, use the documentation, community, support channel, or administrator contact information provided by the current SkillHub instance.',
        ],
      },
    ],
  },
} as const

export function PrivacyPolicyPage() {
  const { i18n } = useTranslation()
  const language = i18n.resolvedLanguage?.split('-')[0] === 'zh' ? 'zh' : 'en'

  return <LegalDocument {...privacyDocuments[language]} />
}
