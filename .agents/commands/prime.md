---
description: 为代理建立代码库理解
---

# Prime：加载项目上下文

## 目标

通过分析代码库结构、文档、配置和关键实现，快速建立对项目的真实理解，适用于 Java、Go、Python、Rust、Node.js、前端及混合仓库。

## 流程

### 1. 分析项目结构

列出所有被追踪的文件：
!`git ls-files`

查看目录结构：
- 优先查看根目录以及主要子目录结构
- 如环境支持，可运行：`tree -L 3 -I 'node_modules|target|dist|build|coverage|.git|__pycache__|.venv|venv|bin|obj'`
- 若仓库是 monorepo，要识别各子项目边界（如 `backend/`、`frontend/`、`services/`、`packages/`、`cmd/`、`crates/`、`apps/`）

### 2. 阅读核心文档

优先阅读并建立文档优先级：
- `AGENTS.md`、`CLAUDE.md`、`README.md`
- `docs/` 下的主入口文档、PRD、验收、设计或归档文档
- 根目录及主要子目录下的 README 文件
- 与当前项目相关的架构说明、接口说明、运行说明

注意：
- 不要假设一定存在 `PRD.md`
- 不要假设文档一定是前端或 Python 风格命名
- 以“当前事实文档 + 代码”作为最高优先级

### 3. 识别项目类型与技术栈

根据文件和目录自动判断项目类型，至少覆盖：
- Java / Kotlin：`pom.xml`、`build.gradle`、`build.gradle.kts`
- Go：`go.mod`
- Python：`pyproject.toml`、`requirements.txt`、`poetry.lock`
- Rust：`Cargo.toml`
- Node.js / 前端：`package.json`、`pnpm-lock.yaml`、`yarn.lock`
- 通用配置：`Dockerfile`、`docker-compose.yml`、`.github/workflows/`、`Makefile`

同时识别：
- 包管理器 / 构建工具
- 测试框架
- 代码格式化 / lint 工具
- 配置文件位置
- 单体仓库、monorepo 或前后端混合仓库结构

### 4. 识别关键文件

基于项目结构，识别并阅读：
- 主入口文件
    - Java：`Application.java`、启动类、`main` 方法入口
    - Go：`cmd/*/main.go`、`main.go`
    - Python：`main.py`、`app.py`、`manage.py`、包入口
    - Rust：`src/main.rs`、`src/lib.rs`
    - Node.js / 前端：`src/main.ts`、`src/index.ts`、`server.ts`、`app.ts`
- 核心配置文件
    - 如 `pom.xml`、`build.gradle`、`go.mod`、`pyproject.toml`、`Cargo.toml`、`package.json`
- 关键领域模型、schema、DTO、接口定义
- 重要的 controller / handler / router / service / facade / agent / repository 文件
- 运行时配置
    - 如 `application.yml`、`.env.example`、`config/`、`resources/`

如果项目涉及数据库，也要识别对应 schema / migration / ORM 配置，而不是只关注 drizzle。

### 5. 理解当前状态

检查最近活动：
!`git log -10 --oneline`

检查当前分支和状态：
!`git status`

如果仓库已有清晰文档，要确认以下边界：
- 当前已实现能力
- 尚未完成 / 待开发能力
- 历史方案、原型或归档内容
- 前端已接入范围与后端已开放范围是否一致

## 输出报告

提供一份简明总结，覆盖以下内容：

### 项目概览
- 应用的目的和类型
- 当前是单体、前后端分离、微服务还是多模块仓库
- 当前实现重点或主要业务链路

### 架构
- 整体结构和组织方式
- 关键模块及职责分层
- 重要目录及其用途

### 技术栈
- 主要语言及版本
- 框架和核心库
- 构建工具、包管理器和运行方式
- 测试框架、格式化和 lint 工具

### 核心原则
- 观察到的代码风格和约定
- 文档主入口与事实来源
- 测试与验证方式
- 需要特别注意的边界或约束

### 当前状态
- 当前活跃分支
- 最近的改动方向
- 当前仓库中显著的进行中工作
- 任何需要立即注意的观察或风险

**让这份总结易于快速浏览：使用清晰的标题和项目符号列表。**
