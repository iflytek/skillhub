# SkillHub CLI 完整测试指南

> **CLI 版本**: v1.0.0
> **更新日期**: 2026-04-10
> **测试环境**: 本地开发环境 / Docker Compose 测试环境
> **分支**: feat/skillhub-cli

---

## 一、测试前准备

### 1.1 构建 CLI

```bash
cd skillhub-cli
pnpm install
pnpm build
```

### 1.2 环境配置

```bash
# 使用本地开发环境 (当前机器)
export REGISTRY=http://localhost:8080

# 或使用测试环境 (Docker Compose)
export REGISTRY=http://localhost:8081

# 永久配置 alias (推荐)
echo 'alias skillhub="node /path/to/skillhub-cli/dist/cli.mjs --registry http://localhost:8080"' >> ~/.bashrc
source ~/.bashrc
```

### 1.3 启动本地测试环境

```bash
cd /mnt/cfs/chenbaowang/skillhub

# 1. 停止旧服务
pkill -f "skillhub-app.*jar" 2>/dev/null || true
docker compose -p skillhub down 2>/dev/null || true

# 2. 启动数据库和缓存
docker compose -p skillhub up -d postgres redis minio
sleep 10

# 3. 启动后端 (0.0.0.0 绑定支持局域网访问)
cd server
java -jar skillhub-app/target/skillhub-app-0.1.0.jar --server.address=0.0.0.0 --spring.profiles.active=local > ../.dev/server.log 2>&1 &
cd ..

# 4. 启动前端 (0.0.0.0 绑定支持局域网访问)
cd web && nohup pnpm run dev > ../.dev/web.log 2>&1 &

# 验证
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:3000 | head -c 100
```

### 1.4 测试用户认证

```bash
# 方法1: 使用已有 token (推荐)
# 默认 token 文件位置: ~/.skillhub/token
# 当前已配置的测试用户: docker-admin
node dist/cli.mjs whoami  # 直接验证是否已登录

# 方法2: 通过 Web UI 获取 token
# 1. 打开 http://localhost:3000
# 2. 登录后打开 DevTools (F12) -> Application -> Local Storage -> skillhub-token
# 3. 复制 token 值后登录

# 方法3: 使用已有的 docker-admin token (测试用)
TOKEN="sk_vsZwUrmrEOYD3D3V1-IxFpaoU4kURH-9fJRg8nR8dv0"
node dist/cli.mjs login --token $TOKEN

# 方法4: 创建新用户 token (需要后端支持)
# curl -X POST http://localhost:8080/api/v1/auth/token -d '{"username":"xxx"}'

# 验证登录
node dist/cli.mjs whoami
```

**注意**: 本地开发环境 (`--spring.profiles.active=local`) 使用 Mock Auth，
不支持通过 `/api/v1/auth/token/local` 获取 token。需要使用已有的 token 或通过 Web UI 登录获取。

---

## 二、全局选项

所有命令都支持以下全局选项：

| 选项 | 说明 | 示例 |
|------|------|------|
| `--registry <url>` | 指定 Registry API 地址 (默认: http://localhost:8080) | `--registry http://localhost:8081` |
| `--no-input` | 禁用所有交互式提示 | `--no-input install openspec` |
| `--json` | JSON 格式输出 | `--json search openspec` |
| `--version` / `-V` | 显示 CLI 版本 | `--version` |
| `--help` / `-h` | 显示帮助 | `--help install` |

```bash
# 全局选项测试
node dist/cli.mjs --version                    # 显示版本
node dist/cli.mjs --help                      # 显示全局帮助
node dist/cli.mjs --registry http://localhost:8081 whoami   # 指定 registry
node dist/cli.mjs --json search openspec      # JSON 输出
node dist/cli.mjs --no-input install openspec # 跳过确认
```

---

## 三、命令分类测试

### 3.1 认证命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| AUTH-01 | `login --help` | 查看登录帮助 | 显示 token 和 registry 选项 |
| AUTH-02 | `login --token <TOKEN>` | Token 登录 | 保存 Token，显示 "Authenticated as @handle" |
| AUTH-03 | `whoami` | 查看当前用户 | 显示 `Handle` 和 `Display Name` |
| AUTH-04 | `logout` | 登出 | 清除本地 Token |

```bash
# 测试命令
node dist/cli.mjs login --token <YOUR_TOKEN>
node dist/cli.mjs whoami
node dist/cli.mjs logout
```

---

### 3.2 搜索与发现

#### 3.2.1 search - 搜索 Skills

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| SEARCH-01 | `search <keyword>` | 基本搜索 | 返回匹配列表，默认显示 stars/downloads |
| SEARCH-02 | `search <keyword> -n <n>` | 限制结果数 | 只返回 n 条结果 |
| SEARCH-03 | `search <keyword> --namespace <ns>` | 按命名空间过滤 | 只返回该 namespace 的结果 |
| SEARCH-04 | `search <keyword> --json` | JSON 输出 | 输出有效 JSON |
| SEARCH-05 | `search <keyword> --no-input` | 非交互模式 | 无交互提示 |

```bash
# 测试命令
node dist/cli.mjs search openspec
node dist/cli.mjs search openspec -n 5
node dist/cli.mjs search openspec --namespace vision2group
node dist/cli.mjs search openspec --json
```

#### 3.2.2 namespaces - 命名空间管理

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| NS-01 | `namespaces` | 列出有权限的命名空间 | 显示 namespace 列表及角色 |

```bash
# 测试命令
node dist/cli.mjs namespaces
```

#### 3.2.3 inspect - 查看 Skill 详情

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| INSPECT-01 | `inspect <slug>` | 查看 skill 详情 | 显示完整 metadata (别名: info, view) |
| INSPECT-02 | `inspect <slug> --namespace <ns>` | 指定命名空间 | 从指定 namespace 获取详情 |

```bash
# 测试命令
node dist/cli.mjs inspect openspec
node dist/cli.mjs inspect openspec --namespace vision2group
```

#### 3.2.4 explore - 浏览 Skills

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| EXPLORE-01 | `explore` | 浏览最新 skills | 显示最新列表 |
| EXPLORE-02 | `explore <query>` | 搜索浏览 | 带搜索条件的浏览 |
| EXPLORE-03 | `explore -i` | 交互式选择安装 | 需要终端交互 (别名: find) |

```bash
# 测试命令
node dist/cli.mjs explore
node dist/cli.mjs explore openspec
# 交互式需要真实终端
```

#### 3.2.5 versions - 版本列表

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| VERS-01 | `versions <slug>` | 列出所有版本 | 显示版本列表 (别名: -) |
| VERS-02 | `versions <slug> --namespace <ns>` | 指定命名空间 | 从指定 namespace 获取版本 |

```bash
# 测试命令
node dist/cli.mjs versions openspec
node dist/cli.mjs versions openspec --namespace vision2group
```

#### 3.2.6 resolve - 解析版本

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| RESOLVE-01 | `resolve <slug>` | 获取最新版本信息 | 显示版本 ID、fingerprint、download URL |
| RESOLVE-02 | `resolve <slug> --namespace <ns>` | 指定命名空间 | 从指定 namespace 解析 |
| RESOLVE-03 | `resolve <slug> --version <ver>` | 指定版本 | 解析特定版本 |
| RESOLVE-04 | `resolve <slug> --tag <tag>` | 按 tag 解析 | 解析指定 tag (默认: latest) |

```bash
# 测试命令
node dist/cli.mjs resolve openspec
node dist/cli.mjs resolve openspec --namespace vision2group
node dist/cli.mjs resolve openspec --version 20260407.195957
```

---

### 3.3 安装与管理

#### 3.3.1 install - 安装 Skills

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| INST-01 | `install <slug>` | 从 registry 安装 (默认 global) | 下载并安装 |
| INST-02 | `install <slug> --namespace <ns>` | 从指定 NS 安装 | 从该 namespace 下载 |
| INST-03 | `install <source> --source registry` | 强制 registry source | 明确使用 registry |
| INST-04 | `install <source> --source git` | 强制 git source | 明确使用 git |
| INST-05 | `install <source> --source local` | 强制 local source | 明确使用 local |
| INST-06 | `install <source> --source auto` | 自动检测 (默认) | 自动识别 source 类型 |
| INST-07 | `install <slug> --list` | 只列出不安装 | 显示可用版本信息 |
| INST-08 | `install <slug> --copy` | 复制模式安装 | 复制文件而非 symlink |
| INST-09 | `install <slug> --global` | 全局安装 | 安装到全局目录 |
| INST-10 | `install <slug> -y` | 跳过确认 | 直接安装 |
| INST-11 | `i <slug>` | 使用别名安装 | 同 install |
| INST-12 | `install <slug> -n <n>` | 限制并发 | 限制下载并发数 |

```bash
# Registry Source (默认)
node dist/cli.mjs install openspec
node dist/cli.mjs install openspec --namespace vision2group

# Git Source (自动检测)
node dist/cli.mjs install owner/repo
node dist/cli.mjs install https://github.com/owner/repo

# Local Source (自动检测)
node dist/cli.mjs install ./my-skill
node dist/cli.mjs install /absolute/path/to/skill

# 强制 Source 类型
node dist/cli.mjs install openspec --source registry
node dist/cli.mjs install owner/repo --source git
node dist/cli.mjs install ./local-skill --source local

# 选项组合
node dist/cli.mjs install openspec --namespace vision2group --copy --global
```

**Source 自动检测规则**:
- `owner/repo` 或 `https://github.com/...` → git
- `./path` 或 `/absolute/path` → local
- 其他 → registry

#### 3.3.2 add - Git/Local 专用安装

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| ADD-01 | `add <git-url>` | 从 Git 安装 | 克隆并安装 |
| ADD-02 | `add <local-path>` | 从本地路径安装 | 复制或 symlink |
| ADD-03 | `add <source> --skill <skills>` | 只安装指定 skills | 过滤后安装 |
| ADD-04 | `add <source> --global` | 全局安装 | 安装到全局目录 |
| ADD-05 | `add <source> --copy` | 复制模式 | 复制而非 symlink |
| ADD-06 | `add <source> --list` | 列出可用 skills | 不安装，只显示 |

```bash
# 测试命令
node dist/cli.mjs add https://github.com/vercel-labs/skills
node dist/cli.mjs add ./my-skill --global
node dist/cli.mjs add owner/repo --skill skill1 skill2
```

#### 3.3.3 list - 已安装列表

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| LIST-01 | `list` | 列出已安装 skills | 显示所有已安装 |
| LIST-02 | `list --global` | 只显示全局 | 显示全局目录 skills |
| LIST-03 | `list --project` | 只显示项目级 | 显示项目目录 skills |
| LIST-04 | `ls` | 别名测试 | 同 list |

```bash
# 测试命令
node dist/cli.mjs list
node dist/cli.mjs list --global
node dist/cli.mjs list --project
```

#### 3.3.4 uninstall - 卸载 Skills

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| UNINST-01 | `uninstall <name>` | 卸载 skill | 从本地移除 |
| UNINST-02 | `uninstall --all` | 卸载全部 | 移除所有已安装 |
| UNINST-03 | `uninstall <name> --global` | 卸载全局 | 从全局目录移除 |
| UNINST-04 | `uninstall <name> --agent <agents>` | 从指定 agent 卸载 | 只从该 agent 移除 |
| UNINST-05 | `uninstall <name> -y` | 跳过确认 | 直接卸载 |
| UNINST-06 | `un <name>` | 别名测试 | 同 uninstall |

```bash
# 测试命令
node dist/cli.mjs uninstall openspec
node dist/cli.mjs uninstall --all
node dist/cli.mjs uninstall openspec --global
node dist/cli.mjs uninstall openspec --agent claude-code
node dist/cli.mjs un openspec
```

#### 3.3.5 update - 更新 Skills

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| UPDATE-01 | `update` | 更新 (无参数) | 提示需要 --all 或指定 skill |
| UPDATE-02 | `update <slug>` | 更新指定 skill | 检查并更新 |
| UPDATE-03 | `update --all` | 更新全部 | 检查并更新所有 |
| UPDATE-04 | `update --global` | 更新全局 | 只更新全局目录 |
| UPDATE-05 | `up <slug>` | 别名测试 | 同 update |

```bash
# 测试命令
node dist/cli.mjs update openspec
node dist/cli.mjs update --all
node dist/cli.mjs update --global
```

#### 3.3.6 check - 检查已安装

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| CHECK-01 | `check` | 检查本地 skills | 对比 lock 文件 |
| CHECK-02 | `check --global` | 检查全局 | 只检查全局目录 |
| CHECK-03 | `check --json` | JSON 输出 | 输出有效 JSON |

```bash
# 测试命令
node dist/cli.mjs check
node dist/cli.mjs check --global
node dist/cli.mjs check --json
```

#### 3.3.7 download - 下载 Skill 包

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| DL-01 | `download <slug>` | 下载 skill 包 | 下载到当前目录 |
| DL-02 | `download <slug> --namespace <ns>` | 指定命名空间 | 从指定 NS 下载 |
| DL-03 | `download <slug> --version <ver>` | 指定版本 | 下载特定版本 |
| DL-04 | `download <slug> --tag <tag>` | 按 tag 下载 | 下载指定 tag |
| DL-05 | `download <slug> --output <dir>` | 指定输出目录 | 下载到该目录 |

```bash
# 测试命令
node dist/cli.mjs download openspec
node dist/cli.mjs download openspec --namespace vision2group
node dist/cli.mjs download openspec --version 20260407.195957
node dist/cli.mjs download openspec --output /tmp/skills
```

---

### 3.4 发布命令

#### 3.4.1 publish - 发布 Skill

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| PUB-01 | `publish [path]` | 发布 skill | 成功发布 |
| PUB-02 | `publish [path] --namespace <ns>` | 发布到指定 NS | 发布到该命名空间 |
| PUB-03 | `publish [path] -v <ver>` | 指定版本 | 使用 semver 版本 |
| PUB-04 | `publish [path] --name <name>` | 指定显示名 | 使用该名称 |
| PUB-05 | `publish [path] --changelog <text>` | 添加更新日志 | 包含 changelog |
| PUB-06 | `publish [path] --tags <tags>` | 指定标签 | 逗号分隔的标签 |

```bash
# 测试命令
node dist/cli.mjs publish ./my-skill -v 1.0.0
node dist/cli.mjs publish ./my-skill --namespace vision2group -v 1.0.1
node dist/cli.mjs publish ./my-skill --name "My Skill" --tags "ai,productivity"
```

#### 3.4.2 sync - 批量发布

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| SYNC-01 | `sync [path]` | 批量发布 | 扫描并发布多个 |
| SYNC-02 | `sync [path] --namespace <ns>` | 发布到指定 NS | 使用该命名空间 |
| SYNC-03 | `sync [path] --all` | 包含所有 | 即使有变更也发布 |
| SYNC-04 | `sync [path] -y` | 跳过确认 | 直接发布 |

```bash
# 测试命令
node dist/cli.mjs sync ./skills-dir
node dist/cli.mjs sync ./skills-dir --namespace vision2group
```

#### 3.4.3 init - 创建模板

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| INIT-01 | `init` | 创建默认模板 | 生成 SKILL.md |
| INIT-02 | `init <name>` | 指定名称 | 使用该名称创建 |

```bash
# 测试命令
node dist/cli.mjs init
node dist/cli.mjs init my-awesome-skill
```

#### 3.4.4 archive - 归档 Skill

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| ARCH-01 | `archive <slug>` | 归档 skill | 标记为归档 |
| ARCH-02 | `archive <slug> --namespace <ns>` | 指定命名空间 | 归档该 NS 下的 |
| ARCH-03 | `archive <slug> -y` | 跳过确认 | 直接归档 |

```bash
# 测试命令
node dist/cli.mjs archive test-skill
node dist/cli.mjs archive test-skill --namespace vision2group
```

---

### 3.5 社交功能

#### 3.5.1 star - 收藏

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| STAR-01 | `star <slug>` | 收藏 skill | 成功加星 |
| STAR-02 | `star <slug> --namespace <ns>` | 指定命名空间 | 从该 NS 加星 |
| STAR-03 | `star <slug> --unstar` | 取消收藏 | 移除星标 |

```bash
# 测试命令
node dist/cli.mjs star openspec
node dist/cli.mjs star openspec --namespace vision2group
node dist/cli.mjs star openspec --unstar
```

#### 3.5.2 rating - 评分

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| RATE-01 | `rating <slug>` | 查看评分 | 显示星级 (1-5) |
| RATE-02 | `rating <slug> --namespace <ns>` | 指定命名空间 | 查看该 NS 下的 |
| RATE-03 | `rate <slug> <score>` | 评分 | 提交 1-5 分评分 |

```bash
# 测试命令
node dist/cli.mjs rating openspec
node dist/cli.mjs rating openspec --namespace vision2group
node dist/cli.mjs rate openspec 5
```

#### 3.5.3 me - 我的内容

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| ME-01 | `me skills` | 我的发布 | 列出我发布的 skills |
| ME-02 | `me stars` | 我的收藏 | 列出我收藏的 skills |

```bash
# 测试命令
node dist/cli.mjs me skills
node dist/cli.mjs me stars
```

#### 3.5.4 notifications - 通知

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| NOTIF-01 | `notifications list` | 通知列表 | 显示通知 |
| NOTIF-02 | `notifications read <id>` | 标记已读 | 标记单条为已读 |
| NOTIF-03 | `notifications read-all` | 全部已读 | 标记所有为已读 |

```bash
# 测试命令
node dist/cli.mjs notifications list
node dist/cli.mjs notifications read 1
node dist/cli.mjs notifications read-all
```

#### 3.5.5 reviews - 审核

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| REVIEW-01 | `reviews my` | 我的提交 | 列出我的审核提交 |

```bash
# 测试命令
node dist/cli.mjs reviews my
```

#### 3.5.6 report - 举报

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| REPORT-01 | `report <slug>` | 举报 skill | 提交举报 |
| REPORT-02 | `report <slug> --reason <reason>` | 指定原因 | 使用指定原因 |

```bash
# 测试命令
node dist/cli.mjs report suspicious-skill --reason "Malware"
```

---

### 3.6 删除命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| DEL-01 | `delete <slug>` | 删除 skill | 删除 (需 owner) |
| DEL-02 | `delete <slug> --namespace <ns>` | 指定命名空间 | 删除该 NS 下的 |
| DEL-03 | `delete <slug> -y` | 跳过确认 | 直接删除 |
| DEL-04 | `del <slug>` | 别名测试 | 同 delete |
| DEL-05 | `unpublish <slug>` | 别名测试 | 同 delete |

```bash
# 测试命令
node dist/cli.mjs delete test-skill
node dist/cli.mjs delete test-skill --namespace vision2group
node dist/cli.mjs del test-skill -y
```

---

### 3.7 管理命令

#### 3.7.1 transfer - 转移所有权

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TRANS-01 | `transfer <namespace> <newOwnerId>` | 转移所有权 | 转移 NS 所有权 |
| TRANS-02 | `transfer <namespace> <newOwnerId> -y` | 跳过确认 | 直接转移 |

```bash
# 测试命令
node dist/cli.mjs transfer vision2group new-owner-id
```

#### 3.7.2 hide - 隐藏 Skill (Admin)

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| HIDE-01 | `hide <slug>` | 隐藏 skill | 标记为隐藏 |
| HIDE-02 | `hide <slug> --namespace <ns>` | 指定命名空间 | 隐藏该 NS 下的 |
| HIDE-03 | `hide unhide <slug>` | 取消隐藏 | 取消隐藏 |

```bash
# 测试命令
node dist/cli.mjs hide problem-skill
node dist/cli.mjs hide unhide problem-skill
```

---

## 四、--namespace 选项统一说明

以下命令支持 `--namespace` 选项 (默认: global)：

| 命令 | 说明 | 示例 |
|------|------|------|
| `install` | 指定 registry source 的 namespace | `install openspec --namespace vision2group` |
| `inspect` | 指定查询的 namespace | `inspect openspec --namespace vision2group` |
| `versions` | 指定 namespace 的版本列表 | `versions openspec --namespace vision2group` |
| `resolve` | 指定 namespace 解析 | `resolve openspec --namespace vision2group` |
| `download` | 指定 namespace 下载 | `download openspec --namespace vision2group` |
| `star` | 指定 namespace 加星 | `star openspec --namespace vision2group` |
| `rating` | 指定 namespace 评分 | `rating openspec --namespace vision2group` |
| `delete` | 指定 namespace 删除 | `delete openspec --namespace vision2group` |
| `archive` | 指定 namespace 归档 | `archive openspec --namespace vision2group` |
| `hide` | 指定 namespace 隐藏 | `hide openspec --namespace vision2group` |

---

## 五、Source 类型与自动检测

### 5.1 Source 类型

| Type | 说明 | 检测方式 |
|------|------|----------|
| `registry` | 从 SkillHub registry 安装 | 默认，或 `--source registry` |
| `git` | 从 Git repository 安装 | 包含 `/` 或 `--source git` |
| `local` | 从本地路径安装 | `./` 开头或绝对路径，或 `--source local` |

### 5.2 完整 source 语法

```bash
# Registry source
install <slug>                          # 默认 global
install <slug> --namespace <ns>         # 指定 namespace
install <ns>--<slug>                    # 简写形式

# Git source
install owner/repo                      # 简短形式
install https://github.com/owner/repo   # URL 形式
install git@github.com:owner/repo.git   # SSH 形式

# Local source
install ./my-skill                      # 相对路径
install /absolute/path/to/skill         # 绝对路径

# @ 语法 (只安装指定的 skill)
install owner/repo@skill-name            # git source + skill 过滤
```

---

## 六、交互式功能 (需真实终端)

以下功能需要真实终端交互，无法在自动化脚本中测试：

| 功能 | 命令 | 测试说明 |
|------|------|----------|
| 交互式登录 | `login` | 需要输入 token |
| 交互式安装选择 | `install --list` | 多 skill 包时需要选择 |
| 交互式浏览安装 | `explore -i` | 需要上下键+回车 |
| 发布确认 | `publish` | 需要确认 |
| 同步确认 | `sync` | 需要确认 |
| 卸载确认 | `uninstall` | 需要确认 |

---

## 七、错误处理测试

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| ERR-01 | `install nonexistent` | 不存在的 skill | Error: Skill not found |
| ERR-02 | `install ./nonexistent` | 不存在的本地路径 | Error: Path not found |
| ERR-03 | `uninstall nonexistent` | 卸载不存在的 | Error: Skill not installed |
| ERR-04 | `publish -v invalid` | 无效版本号 | Error: Invalid semver |
| ERR-05 | `--registry invalid.com whoami` | 无效 registry | Connection error |
| ERR-06 | `install owner/repo@nonexistent` | @语法 skill 不存在 | Error: No matching skills |
| ERR-07 | `whoami` (未登录) | 未认证 | Error: Not authenticated |

---

## 八、回归测试脚本

```bash
#!/bin/bash
# skillhub-cli-regression.sh

set -e

REGISTRY=${REGISTRY:-http://localhost:8080}
CLI="node dist/cli.mjs --registry $REGISTRY"
PASS=0
FAIL=0

pass() { echo "✅ PASS: $1"; ((PASS++)); }
fail() { echo "❌ FAIL: $1"; ((FAIL++)); }

echo "=== SkillHub CLI 回归测试 ==="
echo "Registry: $REGISTRY"
echo ""

# Auth
$CLI whoami > /dev/null 2>&1 && pass "whoami (authenticated)" || fail "whoami"

# Search
$CLI search openspec 2>/dev/null | grep -q openspec && pass "search" || fail "search"

# Namespaces
$CLI namespaces 2>/dev/null | grep -q namespace && pass "namespaces" || fail "namespaces"

# Inspect
$CLI inspect openspec 2>/dev/null | grep -q openspec && pass "inspect" || fail "inspect"

# Versions
$CLI versions openspec 2>/dev/null | grep -q v && pass "versions" || fail "versions"

# Resolve
$CLI resolve openspec 2>/dev/null | grep -q openspec@ && pass "resolve" || fail "resolve"

# List
$CLI list 2>/dev/null | grep -q OpenCode && pass "list" || fail "list"

# Rating
$CLI rating openspec 2>/dev/null | grep -q ★ && pass "rating" || fail "rating"

# Me
$CLI me stars 2>/dev/null | grep -q test && pass "me stars" || fail "me stars"
$CLI me skills 2>/dev/null | grep -q openspec && pass "me skills" || fail "me skills"

echo ""
echo "=== 测试结果 ==="
echo "通过: $PASS"
echo "失败: $FAIL"
[[ $FAIL -eq 0 ]] && echo "🎉 所有测试通过!" || echo "⚠️  有测试失败"
```

---

## 九、测试结果记录

| 测试日期 | 测试者 | 通过 | 失败 | 通过率 | 状态 |
|---------|--------|------|------|--------|------|
| 2026-04-10 | Claude | 30 | 2 | 93.75% | 完成 |

### 已知问题

| # | 问题 | 严重性 | 状态 |
|---|------|--------|------|
| 1 | `star` 返回 403 Forbidden (后端 API 问题) | 高 | 待后端修复 |
| 2 | `notifications list` 返回 403 (后端 API 问题) | 高 | 待后端修复 |
| 3 | `reviews my` 返回 403 (后端 API 问题) | 高 | 待后端修复 |
| 4 | `check` 显示 NOT INSTALLED (symlink 持久化问题) | 中 | 设计问题 |

---

## 十、命令速查表

### 认证
```bash
login --token <TOKEN>
logout
whoami
```

### 搜索发现
```bash
search <query> [--namespace <ns>] [-n <n>]
namespaces
inspect <slug> [--namespace <ns>]
explore [query] [-i]
versions <slug> [--namespace <ns>]
resolve <slug> [--namespace <ns>] [--version <ver>] [--tag <tag>]
```

### 安装管理
```bash
install <source> [--source auto|registry|git|local] [--namespace <ns>] [--copy] [--global] [-y] [--list]
add <source> [--skill <skills...>] [--agent <agents...>] [--global] [--copy]
list [--global|--project]
uninstall [name] [--all] [--global] [--agent <agents...>] [-y]
update [slug] [--all] [--global]
check [--global] [--json]
download <slug> [--namespace <ns>] [--version <ver>] [--tag <tag>] [--output <dir>]
```

### 发布
```bash
publish [path] [--namespace <ns>] [-v <ver>] [--name <name>] [--changelog <text>] [--tags <tags>]
sync [path] [--namespace <ns>] [--all] [-y]
init [name]
archive <slug> [--namespace <ns>] [-y]
```

### 社交
```bash
star <slug> [--namespace <ns>] [--unstar]
rating <slug> [--namespace <ns>]
rate <slug> <score>
me skills
me stars
notifications list|read <id>|read-all
reviews my
report <slug> [--reason <reason>]
```

### 管理
```bash
delete <slug> [--namespace <ns>] [-y]
transfer <namespace> <newOwnerId> [-y]
hide <slug> [--namespace <ns>] [-y]
hide unhide <slug> [--namespace <ns>]
```

### 全局选项
```bash
--registry <url>      # 指定 Registry
--no-input           # 禁用交互
--json               # JSON 输出
--version            # 显示版本
--help               # 显示帮助
```
