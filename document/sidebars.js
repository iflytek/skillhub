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
        'getting-started/overview',
        'getting-started/quick-start',
        'getting-started/use-cases',
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
            'administration/deployment/single-machine',
            'administration/deployment/kubernetes',
            'administration/deployment/configuration',
          ],
        },
        {
          type: 'category',
          label: '安全与合规',
          items: [
            'administration/security/authentication',
            'administration/security/authorization',
            'administration/security/audit-logs',
          ],
        },
        {
          type: 'category',
          label: '治理与运营',
          items: [
            'administration/governance/namespaces',
            'administration/governance/review-workflow',
            'administration/governance/user-management',
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
            'user-guide/publishing/create-skill',
            'user-guide/publishing/publish',
            'user-guide/publishing/versioning',
          ],
        },
        {
          type: 'category',
          label: '发现与使用',
          items: [
            'user-guide/discovery/search',
            'user-guide/discovery/install',
            'user-guide/discovery/ratings',
          ],
        },
        {
          type: 'category',
          label: '协作',
          items: [
            'user-guide/collaboration/namespaces',
            'user-guide/collaboration/promotion',
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
            'developer/api/overview',
            'developer/api/public',
            'developer/api/authenticated',
            'developer/api/cli-compat',
          ],
        },
        {
          type: 'category',
          label: '架构设计',
          items: [
            'developer/architecture/overview',
            'developer/architecture/domain-model',
            'developer/architecture/security',
          ],
        },
        {
          type: 'category',
          label: '扩展与集成',
          items: [
            'developer/plugins/skill-protocol',
            'developer/plugins/storage-spi',
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
        'reference/faq',
        'reference/troubleshooting',
        'reference/changelog',
        'reference/roadmap',
      ],
    },
  ],
};

export default sidebars;
