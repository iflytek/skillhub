---
title: SkillHub 文档中心
sidebar_position: 1
description: 企业级 AI 技能注册表 - 安全可控的技能发布、发现与管理平台
---

# SkillHub

<section className="hero-section">
  <div className="container">
    <h1 className="hero-section__title">🏢 企业级 AI 技能注册表</h1>
    <p className="hero-section__tagline">
      安全可控的技能发布、发现与管理平台，保障企业数据主权
    </p>
    <div className="hero-section__cta">
      <a href="/getting-started/quick-start" className="btn-primary">立即部署</a>
      <a href="/getting-started/overview" className="btn-secondary">了解更多</a>
    </div>
  </div>
</section>

---

## 企业价值

<div className="row" style={{ marginTop: '40px', marginBottom: '40px' }}>
  <div className="col col--3">
    <div className="enterprise-value-card">
      <div className="enterprise-value-card__icon">🔐</div>
      <h3 className="enterprise-value-card__title">数据主权可控</h3>
      <p className="enterprise-value-card__description">
        自托管部署，数据不离开企业网络；支持私有 S3/MinIO 存储；完整审计链路
      </p>
    </div>
  </div>
  <div className="col col--3">
    <div className="enterprise-value-card">
      <div className="enterprise-value-card__icon">🏢</div>
      <h3 className="enterprise-value-card__title">治理体系完善</h3>
      <p className="enterprise-value-card__description">
        命名空间隔离；双层审核机制；细粒度 RBAC 权限控制
      </p>
    </div>
  </div>
  <div className="col col--3">
    <div className="enterprise-value-card">
      <div className="enterprise-value-card__icon">🔌</div>
      <h3 className="enterprise-value-card__title">集成能力强</h3>
      <p className="enterprise-value-card__description">
        兼容 ClawHub CLI；标准 REST API；OAuth2 企业 SSO 集成
      </p>
    </div>
  </div>
  <div className="col col--3">
    <div className="enterprise-value-card">
      <div className="enterprise-value-card__icon">📊</div>
      <h3 className="enterprise-value-card__title">可观测性完善</h3>
      <p className="enterprise-value-card__description">
        完整审计日志；Prometheus 指标；操作追踪与溯源
      </p>
    </div>
  </div>
</div>

---

## 核心功能特性

<div style={{ textAlign: 'center', marginTop: '40px' }}>
  <div className="feature-tags">
    <span className="feature-tag">版本控制</span>
    <span className="feature-tag">全文搜索</span>
    <span className="feature-tag">命名空间</span>
    <span className="feature-tag">审核流程</span>
    <span className="feature-tag">语义化版本</span>
    <span className="feature-tag">多维度筛选</span>
    <span className="feature-tag">RBAC 权限</span>
    <span className="feature-tag">审计日志</span>
  </div>
</div>

---

## 快速开始

<div style={{ textAlign: 'center', marginTop: '40px' }}>
  <div className="quick-start-code">
    <code>$ curl -fsSL https://raw.githubusercontent.com/iflytek/skillhub/main/scripts/runtime.sh | sh -s -- up</code>
  </div>
  <p style={{ marginTop: '16px', color: 'var(--ifm-font-color-secondary)' }}>
    访问 <a href="http://localhost:3000">http://localhost:3000</a> 开始使用
  </p>
</div>

---

## 下一步

- [快速开始](./getting-started/quick-start) - 一键启动 SkillHub
- [产品概述](./getting-started/overview) - 了解更多产品特性
- [部署指南](./administration/deployment/single-machine) - 生产环境部署
