---
title: 常见问题
sidebar_position: 1
description: 常见问题解答
---

# 常见问题

## 部署相关

### 如何修改默认端口？

修改 `.env.release` 中的端口配置。

### 如何配置 HTTPS？

建议使用反向代理（Nginx/Ingress）处理 TLS 终止。

### 数据库如何备份？

使用 PostgreSQL 标准备份工具（pg_dump）。

## 使用相关

### 如何重置管理员密码？

如果忘记管理员密码，可通过环境变量重新设置首登管理员，或直接操作数据库。

### 技能包上传失败怎么办？

检查：
1. 文件大小是否超限
2. 文件类型是否在白名单内
3. 是否包含必需的 SKILL.md
4. SKILL.md frontmatter 格式是否正确

### 使用 CLI 安装技能时报 `namespace not found`？

多数情况是 CLI 没有指向你自己的 SkillHub 实例，或命名空间格式不对：

1. **配置 registry 并登录**：用环境变量或 `--registry` 指向你的实例，例如
   `clawhub --registry https://skillhub.your-company.com install <skill>`；登录需要先在 Web 控制台生成 API Token。
2. **命名空间 slug 格式**：全局命名空间的技能直接用名字（如 `my-skill`）；团队命名空间要用 `team--skill` 的形式（`@team/skill` → `team--skill`）。
3. 最稳妥的方式是直接在 SkillHub Web 界面点技能的「安装」按钮，复制其中已经带好正确 registry 与命名空间的命令。

> SkillHub 同时提供 `clawhub` 和 `skillhub` 两种 CLI，用法见各自 README；通过 OpenClaw 对话安装技能时，底层同样调用 CLI。

## 开发相关

### 如何扩展 OAuth Provider？

参考现有 GitHub 实现，添加新的 OAuth Provider 配置。

### 如何自定义搜索实现？

实现 `SearchIndexService` 和 `SearchQueryService` 接口。

## 下一步

- [故障排查](./troubleshooting) - 问题诊断
