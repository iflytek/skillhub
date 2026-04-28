# SkillHub 项目外部依赖分析

> 生成时间: 2026-04-28

本文档记录 SkillHub 项目的所有外部依赖，包括前端、后端、基础设施服务和外部 API。

---

## 目录

- [1. 前端依赖](#1-前端依赖)
- [2. 后端依赖](#2-后端依赖)
- [3. 扫描器依赖](#3-扫描器依赖)
- [4. 基础设施服务](#4-基础设施服务)
- [5. 外部服务依赖](#5-外部服务依赖)
- [6. 项目架构概览](#6-项目架构概览)

---

## 1. 前端依赖

**配置文件**: `web/package.json`

**技术栈**: React 19 + TypeScript 5.7 + Vite 6

### 1.1 核心框架

| 依赖 | 版本 | 用途 |
|------|------|------|
| react | ^19.0.0 | UI 框架 |
| react-dom | ^19.0.0 | DOM 渲染 |
| typescript | ^5.7.0 | 类型系统 |

### 1.2 路由与状态管理

| 依赖 | 版本 | 用途 |
|------|------|------|
| @tanstack/react-router | ^1.95.0 | 类型安全路由 |
| @tanstack/react-query | ^5.64.0 | 服务端状态管理 |
| zustand | ^5.0.11 | 轻量级状态管理 |

### 1.3 UI 组件库

| 依赖 | 版本 | 用途 |
|------|------|------|
| @radix-ui/react-dropdown-menu | ^2.1.16 | 下拉菜单组件 |
| @radix-ui/react-select | ^2.2.6 | 选择器组件 |
| lucide-react | ^0.344.0 | 图标库 |
| sonner | ^2.0.7 | Toast 通知 |
| class-variance-authority | ^0.7.0 | 组件变体管理 |
| tailwindcss | ^3.4.0 | CSS 框架 |
| autoprefixer | ^10.4.0 | CSS 前缀处理 |
| postcss | ^8.4.0 | CSS 转换工具 |

### 1.4 国际化

| 依赖 | 版本 | 用途 |
|------|------|------|
| i18next | ^25.8.18 | 国际化框架 |
| react-i18next | ^16.5.8 | React 绑定 |
| i18next-browser-languagedetector | ^8.2.1 | 浏览器语言检测 |

### 1.5 Markdown 与代码高亮

| 依赖 | 版本 | 用途 |
|------|------|------|
| react-markdown | ^10.1.0 | Markdown 渲染 |
| rehype-highlight | ^7.0.2 | 代码语法高亮 |
| rehype-sanitize | ^6.0.0 | HTML 安全清理 |
| remark-gfm | ^4.0.1 | GitHub 风格 Markdown |
| remark-frontmatter | ^5.0.0 | Frontmatter 解析 |
| lowlight | ^3.3.0 | 代码语法高亮引擎 |
| unist-util-visit | ^5.0.0 | AST 遍历工具 |

### 1.6 API 与文件处理

| 依赖 | 版本 | 用途 |
|------|------|------|
| openapi-fetch | ^0.13.8 | OpenAPI 客户端 |
| react-dropzone | ^15.0.0 | 文件拖拽上传 |
| clsx | ^2.1.0 | 条件类名合并 |
| tailwind-merge | ^2.2.1 | Tailwind 类名合并 |

### 1.7 开发工具

| 依赖 | 版本 | 用途 |
|------|------|------|
| vite | ^6.1.0 | 构建工具 |
| @vitejs/plugin-react | ^4.3.0 | React 插件 |
| eslint | ^8.57.0 | 代码检查 |
| @typescript-eslint/eslint-plugin | ^7.0.0 | TS ESLint 插件 |
| @typescript-eslint/parser | ^7.0.0 | TS ESLint 解析器 |
| eslint-plugin-react-hooks | ^4.6.0 | React Hooks 规则 |
| eslint-plugin-react-refresh | ^0.4.5 | 热更新检查 |
| @types/react | ^19.0.0 | React 类型 |
| @types/react-dom | ^19.0.0 | React DOM 类型 |
| @types/mdast | ^4.0.4 | mdast 类型定义 |
| openapi-typescript | ^7.6.1 | OpenAPI TypeScript 生成 |
| @playwright/test | ^1.58.2 | E2E 测试框架 |
| vitest | ^3.2.4 | 单元测试框架 |

---

## 2. 后端依赖

**配置文件**: `server/` (Maven 多模块项目)

**技术栈**: Spring Boot 3.2.3 + Java 21

### 2.1 模块结构

```
skillhub-parent
├── skillhub-app          # 应用层
├── skillhub-domain       # 领域层
├── skillhub-auth         # 认证模块
├── skillhub-search       # 搜索模块
├── skillhub-storage      # 存储模块
├── skillhub-infra        # 基础设施层
└── skillhub-notification # 通知模块
```

### 2.2 核心框架

所有 Spring Boot 模块都依赖的父级依赖:

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.3 | 应用框架 |
| Java | 21 | 运行时 |
| spring-boot-starter-web | - | Web 服务 |
| spring-boot-starter-data-jpa | - | JPA 持久化 |
| spring-boot-starter-test | - | 测试支持 |

### 2.3 数据库

| 依赖 | 版本 | 用途 |
|------|------|------|
| PostgreSQL | 16 | 主数据库 |
| H2 Database | - | 测试数据库 |
| Flyway Core | - | 数据库迁移 |
| Flyway PostgreSQL | 10.10.0 | PostgreSQL 迁移支持 |
| Hibernate ORM | - | ORM 框架 |

### 2.4 缓存与会话

| 依赖 | 版本 | 用途 |
|------|------|------|
| Redis | 7 | 缓存与会话存储 |
| Spring Session Redis | - | 分布式会话管理 |
| Redisson | 3.51.0 | Redis 客户端 |

### 2.5 认证授权

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Security | - | 安全框架 |
| Spring OAuth2 Client | - | OAuth2 社交登录 |
| Spring Mail | - | 邮件服务 |

### 2.6 对象存储

| 依赖 | 版本 | 用途 |
|------|------|------|
| AWS S3 SDK | 2.20.26 | S3 兼容存储 |
| AWS Apache Client | 2.20.26 | HTTP 客户端 |

### 2.7 搜索分词

| 依赖 | 版本 | 用途 |
|------|------|------|
| jieba-analysis | 1.0.3.1 | 中文分词 |

### 2.8 API 文档

| 依赖 | 版本 | 用途 |
|------|------|------|
| springdoc-openapi-starter-webmvc-ui | 2.3.0 | OpenAPI/Swagger 文档 |

### 2.9 监控运维

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot Actuator | - | 应用监控端点 |
| Micrometer Prometheus | - | Prometheus 指标导出 |
| Spring Boot DevTools | - | 开发时热重载 |

### 2.10 其他工具库

| 依赖 | 版本 | 用途 |
|------|------|------|
| Jackson Databind | - | JSON 处理 |
| SnakeYAML | - | YAML 解析 |
| Jakarta Persistence API | - | JPA 规范 |

### 2.11 测试依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| JUnit Jupiter | - | 单元测试 |
| Mockito | - | Mock 框架 |
| AssertJ | - | 断言库 |
| Spring Security Test | - | 安全测试支持 |

### 2.12 响应式编程

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring WebFlux | - | 响应式 Web 框架 |

---

## 3. 扫描器依赖

**配置文件**: `scanner/Dockerfile`

| 依赖 | 来源 | 版本 | 用途 |
|------|------|------|------|
| Python | alpine | 3.11 | 运行时环境 |
| cisco-ai-skill-scanner | PyPI | latest | AI 技能扫描服务 |

**构建依赖**:
- gcc, musl-dev, libffi-dev (编译依赖)

---

## 4. 基础设施服务

**配置文件**: `docker-compose.yml`

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL | postgres:16-alpine | 5432 | 关系数据库 |
| Redis | redis:7-alpine | 6379 | 缓存与会话存储 |
| MinIO | minio/minio:latest | 9000/9001 | S3 兼容对象存储 |
| Skill Scanner | 本地构建 | 8000 | AI 技能分析服务 |

### 4.1 MinIO 配置

- 控制台端口: 9001
- 默认凭证: minioadmin / minioadmin
- 数据持久化: minio_data 卷

---

## 5. 外部服务依赖

**配置方式**: 环境变量

| 服务 | 环境变量 | 用途 |
|------|----------|------|
| LLM API Key | SKILL_SCANNER_LLM_API_KEY | AI 扫描的 LLM 调用凭证 |
| LLM Base URL | SKILL_SCANNER_LLM_BASE_URL | LLM API 端点地址 |
| LLM Model | SKILL_SCANNER_LLM_MODEL | 指定使用的 LLM 模型 |

---

## 6. 项目架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                           SkillHub                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────┐     ┌─────────────────────────────────────────┐   │
│  │   Web UI    │     │              Server (Java)                │   │
│  │  (React)    │────▶│  ┌─────────┐  ┌─────────┐  ┌─────────┐  │   │
│  │             │     │  │ skillhub│  │ skillhub│  │ skillhub│  │   │
│  │ - React 19  │     │  │   -app  │  │  -auth  │  │ -search │  │   │
│  │ - TanStack  │     │  └─────────┘  └─────────┘  └─────────┘  │   │
│  │ - Tailwind  │     │  ┌─────────┐  ┌─────────┐  ┌─────────┐  │   │
│  │ - i18next   │     │  │skillhub │  │skillhub │  │skillhub │  │   │
│  │ - Vite 6    │     │  │-storage │  │  -infra │  │-domain  │  │   │
│  └─────────────┘     │  └─────────┘  └─────────┘  └─────────┘  │   │
│                      └─────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────┐     ┌─────────────────────────────────────────┐   │
│  │   Scanner   │     │            Infrastructure               │   │
│  │  (Python)   │     │  ┌─────────┐  ┌─────────┐  ┌─────────┐  │   │
│  │             │     │  │PostgreSQL│  │  Redis  │  │  MinIO  │  │   │
│  │ - Cisco AI  │     │  │   :5432  │  │  :6379  │  │:9000/1  │  │   │
│  │   Scanner   │     │  └─────────┘  └─────────┘  └─────────┘  │   │
│  └─────────────┘     └─────────────────────────────────────────┘   │
│                                                                      │
│                          External APIs                               │
│                    ┌─────────────────────────────┐                   │
│                    │  LLM (OpenAI/Anthropic/...) │                   │
│                    └─────────────────────────────┘                   │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.1 技术栈总结

| 层级 | 技术 | 说明 |
|------|------|------|
| 前端框架 | React 19 | 现代 React 生态 |
| 前端构建 | Vite 6 | 快速开发体验 |
| 前端样式 | Tailwind CSS 3 | 原子化 CSS |
| 后端框架 | Spring Boot 3.2 | Java 主流框架 |
| 编程语言 | Java 21 | LTS 版本 |
| 数据库 | PostgreSQL 16 | 关系型数据库 |
| 缓存 | Redis 7 | KV 存储 |
| 对象存储 | MinIO | S3 兼容 |
| 搜索 | Jieba 分词 | 中文处理 |
| 容器化 | Docker Compose | 本地开发 |
| 测试 | Playwright + Vitest | E2E + 单元 |
| 国际化 | i18next | 多语言支持 |

---

## 附录: 环境变量参考

### 开发环境

```bash
# PostgreSQL
POSTGRES_IMAGE=postgres:16-alpine

# Redis
REDIS_IMAGE=redis:7-alpine

# MinIO
MINIO_IMAGE=minio/minio:latest

# LLM 配置
SKILL_SCANNER_LLM_API_KEY=
SKILL_SCANNER_LLM_BASE_URL=
SKILL_SCANNER_LLM_MODEL=
```

### 生产环境参考

参见 `.env.release.example` 文件。
