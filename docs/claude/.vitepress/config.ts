import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'SkillHub',
  description: 'Enterprise Self-hosted Agent Skill Registry',
  base: '/skillhub/',
  ignoreDeadLinks: [/^http:\/\/localhost/],

  head: [],

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
          { text: '功能指南', link: '/guide/skill-publish' },
          { text: 'FAQ', link: '/faq' },
        ],
        sidebar: [
          {
            text: '开始使用',
            items: [
              { text: '项目简介', link: '/introduction' },
              { text: '快速开始', link: '/quickstart' },
            ],
          },
          {
            text: '核心功能',
            items: [
              { text: 'Skill 发布与版本管理', link: '/guide/skill-publish' },
              { text: 'Skill 搜索与发现', link: '/guide/skill-discovery' },
              { text: '命名空间与团队管理', link: '/guide/namespace' },
              { text: '审核与治理', link: '/guide/review' },
              { text: '安全扫描', link: '/guide/scanner' },
              { text: '用户交互与社交', link: '/guide/social' },
            ],
          },
          {
            text: '更多',
            items: [
              { text: '常见问题', link: '/faq' },
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
          { text: 'Guide', link: '/en/guide/skill-publish' },
          { text: 'FAQ', link: '/en/faq' },
        ],
        sidebar: [
          {
            text: 'Getting Started',
            items: [
              { text: 'Introduction', link: '/en/introduction' },
              { text: 'Quick Start', link: '/en/quickstart' },
            ],
          },
          {
            text: 'Core Features',
            items: [
              { text: 'Skill Publishing & Versioning', link: '/en/guide/skill-publish' },
              { text: 'Skill Search & Discovery', link: '/en/guide/skill-discovery' },
              { text: 'Namespace & Team Management', link: '/en/guide/namespace' },
              { text: 'Review & Governance', link: '/en/guide/review' },
              { text: 'Security Scanning', link: '/en/guide/scanner' },
              { text: 'Social & Interaction', link: '/en/guide/social' },
            ],
          },
          {
            text: 'More',
            items: [
              { text: 'FAQ', link: '/en/faq' },
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