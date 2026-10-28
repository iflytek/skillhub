import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'SkillHub',
  description: 'Enterprise Self-hosted Agent Skill Registry',
  base: '/skillhub/',
  ignoreDeadLinks: [/^http:\/\/localhost/],

  head: [],
  vite: {
    build: {
      target: 'es2020',
    },
  },

  // Define root locale for redirect
  locales: {
    root: {
      label: '中文',
      lang: 'zh-CN',
      description: '企业级自托管 Agent Skill 注册中心',
      themeConfig: {
        nav: [
          { text: '首页', link: '/' },
          { text: '快速开始', link: '/quickstart' },
          { text: '文档导航', link: '/guide/' },
          { text: '开源周报', link: 'https://iflytek.github.io/skillhub/weekly/' },
          { text: 'FAQ', link: '/faq' },
        ],
        sidebar: [
          {
            text: '开始使用',
            items: [
              { text: '项目简介', link: '/introduction' },
              { text: '快速开始', link: '/quickstart' },
              { text: '账号与访问令牌', link: '/guide/account-access' },
              { text: '文档导航', link: '/guide/' },
              { text: '常见问题', link: '/faq' },
            ],
          },
          {
            text: '使用技能',
            items: [
              { text: '搜索与安装技能', link: '/guide/skill-discovery' },
              { text: '管理技能套装', link: '/guide/skill-suites' },
              { text: 'CLI 使用参考', link: '/guide/cli' },
              { text: '收藏、评分与通知', link: '/guide/social' },
            ],
          },
          {
            text: '创作与发布',
            collapsed: true,
            items: [
              { text: '发布与更新技能', link: '/guide/skill-publish' },
              { text: 'Bundle 导入', link: '/guide/suite-bundle' },
            ],
          },
          {
            text: '团队与治理',
            collapsed: true,
            items: [
              { text: '管理命名空间', link: '/guide/namespace' },
              { text: '审核技能与套装', link: '/guide/review' },
              { text: '安全扫描与报告', link: '/guide/scanner' },
            ],
          },
          {
            text: '部署与运维',
            collapsed: true,
            items: [
              { text: 'Docker 部署', link: '/guide/deployment' },
              { text: 'Kubernetes 部署', link: '/guide/kubernetes' },
              { text: '技能头像与审核', link: '/guide/skill-avatars' },
            ],
          },
          {
            text: '开发与集成',
            collapsed: true,
            items: [
              { text: 'Runtime 集成参考', link: '/guide/runtime-integration' },
              { text: '源码开发手册', link: 'https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md' },
            ],
          },
        ],
        outline: { label: '页面导航', level: [2, 3] },
        lastUpdated: { text: '最后更新' },
        docFooter: { prev: '上一页', next: '下一页' },
        footer: { message: '版权所有 © 科大讯飞股份有限公司' },
      },
    },
    en: {
      label: 'English',
      lang: 'en',
      link: '/en/',
      description: 'Enterprise Self-hosted Agent Skill Registry',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en/' },
          { text: 'Quick Start', link: '/en/quickstart' },
          { text: 'Guides', link: '/en/guide/' },
          { text: 'Weekly Reports', link: 'https://iflytek.github.io/skillhub/weekly/' },
          { text: 'FAQ', link: '/en/faq' },
        ],
        sidebar: [
          {
            text: 'Start',
            items: [
              { text: 'Introduction', link: '/en/introduction' },
              { text: 'Quick start', link: '/en/quickstart' },
              { text: 'Accounts and access tokens', link: '/en/guide/account-access' },
              { text: 'Documentation map', link: '/en/guide/' },
              { text: 'FAQ', link: '/en/faq' },
            ],
          },
          {
            text: 'Use skills',
            items: [
              { text: 'Find and install skills', link: '/en/guide/skill-discovery' },
              { text: 'Manage skill suites', link: '/en/guide/skill-suites' },
              { text: 'CLI reference', link: '/en/guide/cli' },
              { text: 'Stars, ratings and notifications', link: '/en/guide/social' },
            ],
          },
          {
            text: 'Author skills',
            collapsed: true,
            items: [
              { text: 'Publish and update skills', link: '/en/guide/skill-publish' },
              { text: 'Import a bundle', link: '/en/guide/suite-bundle' },
            ],
          },
          {
            text: 'Team governance',
            collapsed: true,
            items: [
              { text: 'Manage namespaces', link: '/en/guide/namespace' },
              { text: 'Review submissions', link: '/en/guide/review' },
              { text: 'Configure scanning', link: '/en/guide/scanner' },
            ],
          },
          {
            text: 'Deploy and operate',
            collapsed: true,
            items: [
              { text: 'Deploy with Docker', link: '/en/guide/deployment' },
              { text: 'Deploy on Kubernetes', link: '/en/guide/kubernetes' },
              { text: 'Skill avatars and moderation', link: '/en/guide/skill-avatars' },
            ],
          },
          {
            text: 'Develop and integrate',
            collapsed: true,
            items: [
              { text: 'Integrate a runtime', link: '/en/guide/runtime-integration' },
              { text: 'Development handbook (中文)', link: 'https://github.com/iflytek/skillhub/blob/main/docs/dev-workflow.md' },
            ],
          },
        ],
        outline: { label: 'On this page', level: [2, 3] },
        lastUpdated: { text: 'Last updated' },
        docFooter: { prev: 'Previous', next: 'Next' },
        footer: { message: 'Copyright © iFlytek Co., Ltd.' },
      },
    },
  },

  themeConfig: {
    socialLinks: [
      { icon: 'github', link: 'https://github.com/iflytek/skillhub' },
    ],

    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: '搜索文档' },
              modal: {
                noResultsText: '未找到结果',
                resetButtonTitle: '清除搜索',
                footer: { selectText: '选择', navigateText: '切换' },
              },
            },
          },
        },
      },
    },
  },
})
