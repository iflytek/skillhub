# Proposal

## Why

Issue #1110 的实际需求是发现已安装技能的更新并安全批量升级。按已确认的产品边界，仓库成员可以来自不同提交，不应将提交不同直接称为异常，也不应由网页推测用户的本地安装状态。

## What Changes

- 增加 `repository check` 和 `repository upgrade`，基于 #1103 当前可用清单核对既有逐技能 inventory、版本与指纹。
- 保护本地修改、Suite/共享归属和失效来源；明确不可升级与部分失败，不自动增删成员。
- 网页补检查和升级命令入口及对应原型；保留独立安装、快照和 Suite 行为。

## Capabilities

### New Capabilities

- `github-repository-upgrades`: 已安装仓库技能的只读检查、精确版本升级及保护反馈。

### Modified Capabilities

无。

## Impact

依赖 #1103 的只读清单；修改 CLI 升级编排与命令、网页命令入口和用户文档。无新数据库/API、仓库账本、安装遥测或全仓事务。不实施原提案中的自动 Suite、提交锁步、审核重新分组或空间强制策略。
