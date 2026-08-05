---
name: 🌸 HER Hack-Astron 出题
about: 面向企业 Agent Skill 注册、治理、搜索与部署发布 HER Hack-Astron 赛题
title: 'HER Hack-Astron #出题｜赛题名称'
labels: ['HER Hack-Astron']
---

<!-- 替换 {{...}} 后提交；由 @FenjuFu 审核并分配正式期号。 -->

> **赛题确认：** 本 Issue 初始标题为 `HER Hack-Astron #出题｜赛题名称`；经 @FenjuFu 改为 `HER Hack-Astron #期号｜赛题名称` 后正式发布。
>
> **活动标签：** 模板自动添加 `HER Hack-Astron`，未显示时由维护者补充。

## 命题背景

- 出题组织：{{组织名称}}
- 企业技能治理问题：{{发布、发现、审核、权限、审计、部署或兼容性痛点}}
- 目标角色：{{技能作者 / Namespace 管理员 / 平台管理员 / Agent 使用者}}

## SkillHub 赛题方向

围绕**自托管企业 Agent Skill Registry**选择一个可验证方向：

- 技能包发布、语义化版本、标签、推广和回滚
- Namespace RBAC、审核流、API Token、安全扫描与审计日志
- CLI 的 search / install / publish 体验及 Astron Agent、OpenClaw 等客户端兼容
- 全文搜索、权限可见性、排序与可插拔搜索后端
- PostgreSQL 假设解耦、OceanBase MySQL 模式等数据库兼容和迁移
- Docker / Kubernetes、S3 / MinIO、监控与企业内网部署

灵感参考：[OceanBase MySQL 模式部署支持 #247](https://github.com/iflytek/skillhub/issues/247)。

## 任务定义

- 当前限制：{{代码、配置或产品流程中的具体限制}}
- 目标行为：{{用户可观察结果}}
- 影响模块：{{server / web / cli / search / storage / deploy / monitoring}}
- API / SDK 影响：{{是否需更新 OpenAPI 与生成类型}}
- 兼容与迁移：{{旧数据、旧客户端和回滚策略}}

## 最低交付物

- 实现代码及对应单元 / 集成测试
- 涉及数据库时提供可重复迁移、干净实例启动和回滚说明
- 涉及 API 时运行 `make generate-api` 并提交同步的生成文件
- 涉及发布 / 安装时验证 publish → review → search → install 核心链路
- 部署文档、配置示例和脱敏演示记录
- 不提交真实 Token、默认弱密码或私有 Registry 地址

## 验收建议

- `make test` 或受影响模块的项目标准检查通过
- 核心流程在本地开发栈可复现
- Namespace 权限和全局推广边界不被绕过
- 搜索结果遵守可见性；升级不破坏已有技能版本
- 新后端 / 数据库的能力差异和限制有明确文档

## 提交与参与

1. 先在本 Issue 对齐范围，再 Fork 并提交 PR
2. PR 标题：`[HER Hack-Astron #期号] 作品名称 + SkillHub 改进`
3. PR 代码记录中女性贡献者占比须 **≥ 50%**，以 commit / `Co-authored-by:` 为准
4. PR 附架构说明、测试命令、结果和迁移风险

## 评审重点

- 企业治理价值与真实使用场景
- 权限、安全、兼容性与数据迁移质量
- API / CLI / Web 契约一致性
- 测试、可观测性、文档与部署复现

出题 / 合作 / 发奖咨询：ifly_opensource@iflytek.com
