# Proposal

## Why

Issue #1103 希望按 GitHub 仓库批量获取技能。仓库成员的审核和发布时间并不同步，整仓快照 ZIP 不能代替日常安装，也不能将仓库自动转成 Suite。

## What Changes

- 提供只读的仓库安装清单：一次解析全部当前可访问、已发布且可下载的成员，固定精确版本及指纹，允许来自不同提交。
- CLI 新增独立的 `repository install <repository-id>` 命令，复用逐技能安装、锁和本地 inventory；明确逐项成功、跳过和失败，部分失败返回非零。
- 仓库详情增加安装入口、版本清单及可复制命令，说明最新可用版本和完整快照 ZIP 的区别，同步 Figma 原型。
- 不新增仓库安装账本，不改变既有浏览、快照、审核和 Suite 规则；检查更新及升级由 #1110 独立交付。

## Capabilities

### New Capabilities

- `github-repository-installation`: 仓库当前可用成员的安全解析、批量安装及结果呈现。

### Modified Capabilities

无。仓库浏览与完整快照的既有行为保持不变。

## Impact

涉及 app 查询/API、路由与 Token 读取策略、CLI 客户端/命令、仓库详情、生成 API 类型及用户文档。无数据库迁移，无新增生产依赖；旧 CLI 安装、快照 ZIP 和 Suite 命令保持兼容。
