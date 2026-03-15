/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docsSidebar: [
    'index',
    {
      type: 'category',
      label: '快速入门',
      link: {
        type: 'generated-index',
      },
      items: [
        '01-getting-started/overview',
        '01-getting-started/quick-start',
        '01-getting-started/use-cases',
      ],
    },
    {
      type: 'category',
      label: '管理员指南',
      link: {
        type: 'generated-index',
      },
      items: [
        {
          type: 'category',
          label: '部署指南',
          items: [
            '02-administration/deployment/single-machine',
            '02-administration/deployment/kubernetes',
            '02-administration/deployment/configuration',
          ],
        },
        {
          type: 'category',
          label: '安全与合规',
          items: [
            '02-administration/security/authentication',
            '02-administration/security/authorization',
            '02-administration/security/audit-logs',
          ],
        },
        {
          type: 'category',
          label: '治理与运营',
          items: [
            '02-administration/governance/namespaces',
            '02-administration/governance/review-workflow',
            '02-administration/governance/user-management',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '用户指南',
      link: {
        type: 'generated-index',
      },
      items: [
        {
          type: 'category',
          label: '发布技能',
          items: [
            '03-user-guide/publishing/create-skill',
            '03-user-guide/publishing/publish',
            '03-user-guide/publishing/versioning',
          ],
        },
        {
          type: 'category',
          label: '发现与使用',
          items: [
            '03-user-guide/discovery/search',
            '03-user-guide/discovery/install',
            '03-user-guide/discovery/ratings',
          ],
        },
        {
          type: 'category',
          label: '协作',
          items: [
            '03-user-guide/collaboration/namespaces',
            '03-user-guide/collaboration/promotion',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '开发者参考',
      link: {
        type: 'generated-index',
      },
      items: [
        {
          type: 'category',
          label: 'API 参考',
          items: [
            '04-developer/api/overview',
            '04-developer/api/public',
            '04-developer/api/authenticated',
            '04-developer/api/cli-compat',
          ],
        },
        {
          type: 'category',
          label: '架构设计',
          items: [
            '04-developer/architecture/overview',
            '04-developer/architecture/domain-model',
            '04-developer/architecture/security',
          ],
        },
        {
          type: 'category',
          label: '扩展与集成',
          items: [
            '04-developer/plugins/skill-protocol',
            '04-developer/plugins/storage-spi',
          ],
        },
      ],
    },
    {
      type: 'category',
      label: '参考资料',
      link: {
        type: 'generated-index',
      },
      items: [
        '05-reference/faq',
        '05-reference/troubleshooting',
        '05-reference/changelog',
        '05-reference/roadmap',
      ],
    },
  ],
};

export default sidebars;
