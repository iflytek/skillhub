import { defineConfig } from 'vitepress'

export default defineConfig({
  title: '项目档案',
  description: 'SkillHub 项目归档浏览',
  lang: 'zh-CN',

  search: {
    provider: 'local'
  },

  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '时间线', link: '/timeline/2026/2026-04' },
      { text: '叙事', link: '/narratives/origin' },
      { text: '来源', link: '/sources/commits/2026-04-25-repository-commit-history' }
    ],

    sidebar: {
      '/': [
        {
          text: '项目档案',
          items: [
            { text: '首页', link: '/' },
            { text: '档案概述', link: '/README' },
            {
              text: '叙事',
              items: [
                { text: '项目起源', link: '/narratives/origin' },
                { text: '仓库历程', link: '/narratives/repository-history' }
              ]
            },
            {
              text: '时间线',
              items: [
                { text: '2026-03', link: '/timeline/2026/2026-03' },
                { text: '2026-04', link: '/timeline/2026/2026-04' }
              ]
            },
            {
              text: '来源',
              items: [
                { text: '仓库提交历史', link: '/sources/commits/2026-04-25-repository-commit-history' }
              ]
            },
            {
              text: '元信息',
              items: [
                { text: '分类法', link: '/_meta/taxonomy' },
                { text: '贡献指南', link: '/_meta/contribution-guide' },
                { text: '脱敏策略', link: '/_meta/redaction-policy' },
                { text: '变更记录', link: '/_meta/changelog' }
              ]
            },
            {
              text: '索引说明',
              items: [
                { text: '里程碑', link: '/milestones/README' },
                { text: '决策', link: '/decisions/README' },
                { text: '贡献者', link: '/contributors/README' },
                { text: '沟通', link: '/communications/README' },
                { text: '发文', link: '/publications/README' },
                { text: 'GitHub', link: '/github/README' },
                { text: '来源', link: '/sources/README' },
                { text: '收件箱', link: '/inbox/README' },
                { text: '模板', link: '/templates/README' }
              ]
            }
          ]
        }
      ]
    }
  }
})
