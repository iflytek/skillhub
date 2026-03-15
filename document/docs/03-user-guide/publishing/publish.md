---
title: 发布流程
sidebar_position: 2
description: 发布技能到 SkillHub
---

# 发布流程

## 通过 Web 发布

1. 登录 SkillHub
2. 点击"发布技能"
3. 选择目标命名空间
4. 上传技能包 ZIP 文件
5. 填写版本信息（变更日志等）
6. 提交发布
7. 等待审核（如需要）
8. 审核通过后发布成功

## 通过 CLI 发布

```bash
# 1. 登录
skillhub login

# 2. 发布
skillhub publish ./my-skill.zip --namespace @team-myteam
```

## 通过 ClawHub CLI 发布

配置 registry 后使用：

```bash
clawhub publish ./my-skill.zip
```

## 发布状态

| 状态 | 说明 |
|------|------|
| `DRAFT` | 草稿，未提交审核 |
| `PENDING_REVIEW` | 等待审核 |
| `PUBLISHED` | 已发布，可被发现和下载 |
| `REJECTED` | 已拒绝，需修改后重新提交 |
| `YANKED` | 已撤回，不再推荐使用 |

## 下一步

- [版本管理](./versioning) - 管理技能版本
