# SkillHub CLI 完整测试指南

> **CLI 版本**: v1.0.0
> **更新日期**: 2026-04-13
> **测试环境**: 本地开发环境 / Docker Compose 测试环境 / 生产服务器
> **分支**: feat/skillhub-cli

---

## 一、测试前准备

### 1.1 构建 CLI

```bash
cd skillhub-cli && pnpm install && pnpm build
```

### 1.2 环境配置

```bash
# 使用本地开发环境 (当前机器)
export REGISTRY=http://localhost:8080

# 或使用测试环境 (Docker Compose)
export REGISTRY=http://localhost:8081

# 或使用远程服务器 (飞书 OAuth 测试)
export REGISTRY=http://10.0.8.9:8081

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

### 1.4 Token 获取方式

#### 方法 A: Device Code Flow (推荐 - OAuth 方式)

适用于生产环境或需要通过 GitHub/飞书 OAuth 登录的场景：

```bash
# Registry 地址
REGISTRY=${REGISTRY:-http://localhost:8080}

# Step 1: 请求设备码
curl -s -X POST "$REGISTRY/api/v1/auth/device/code" \
  -H "Content-Type: application/json"
```

返回示例:
```json
{
  "data": {
    "deviceCode": "WD4M9XNQKCRK7S9V",
    "userCode": "ABCD-1234",
    "verificationUri": "http://localhost:8080/device-login",
    "interval": 5,
    "expiresIn": 600
  }
}
```

```bash
# Step 2: 在浏览器中打开验证 URL，完成 OAuth 授权
# - 打开: http://localhost:8080/device-login
# - 或直接访问显示的 verificationUri
# - 使用显示的 userCode (如 ABCD-1234) 在页面输入

# Step 3: 轮询获取 token (授权完成后)
curl -s -X POST "$REGISTRY/api/v1/auth/device/token" \
  -H "Content-Type: application/json" \
  -d '{"deviceCode": "你的deviceCode"}'
```

返回示例:
```json
{
  "data": {
    "accessToken": "sk_live_xxxxxxxxxxxx",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

**一键获取 Token 脚本:**
```bash
#!/bin/bash
REGISTRY=${REGISTRY:-http://localhost:8080}
echo "Registry: $REGISTRY"

# Step 1: 请求设备码
DEVICE_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/code" -H "Content-Type: application/json")
DEVICE_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.deviceCode')
USER_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.userCode')
VERIFICATION_URI=$(echo "$DEVICE_RESP" | jq -r '.data.verificationUri')

echo ""
echo "=========================================="
echo "Step 1: 请在浏览器中完成授权"
echo "=========================================="
echo "URL: $VERIFICATION_URI"
echo "User Code: $USER_CODE"
echo ""

# 如果有真实的浏览器交互，可以用 xdg-open/open 自动打开
if command -v xdg-open &> /dev/null; then
  xdg-open "$VERIFICATION_URI" 2>/dev/null || true
elif command -v open &> /dev/null; then
  open "$VERIFICATION_URI" 2>/dev/null || true
fi

read -p "授权完成后按回车继续 (或等待自动轮询)..."

# Step 2: 轮询获取 token
echo ""
echo "=========================================="
echo "Step 2: 获取 Token..."
echo "=========================================="

for i in {1..60}; do
  TOKEN_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/token" \
    -H "Content-Type: application/json" \
    -d "{\"deviceCode\": \"$DEVICE_CODE\"}")
  
  ACCESS_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.data.accessToken // empty')
  
  if [ -n "$ACCESS_TOKEN" ]; then
    echo "✅ 获取成功!"
    echo "Token: $ACCESS_TOKEN"
    echo ""
    echo "=========================================="
    echo "下一步: 登录 CLI"
    echo "=========================================="
    echo "node dist/cli.mjs --registry $REGISTRY login --token $ACCESS_TOKEN"
    exit 0
  fi
  
  echo "等待授权... ($i/60)"
  sleep 5
done

echo "❌ 获取失败，请重试"
exit 1
```

#### 方法 B: 通过 Web UI 获取 (手动)

1. 打开 http://localhost:3000 (或生产环境地址)
2. 使用 GitHub/飞书 OAuth 登录
3. 进入 Settings → API Tokens
4. 创建新 Token
5. 复制 Token

或登录后打开浏览器 DevTools (F12) → Application → Local Storage → 查找 `skillhub-token`

#### 方法 C: 使用已有的测试 Token

```bash
# docker-admin 用户 (本地开发环境)
TOKEN="sk_vsZwUrmrEOYD3D3V1-IxFpaoU4kURH-9fJRg8nR8dv0"
node dist/cli.mjs login --token $TOKEN
```

### 1.5 测试用户认证

```bash
# 验证登录
node dist/cli.mjs whoami

# 查看用户信息
node dist/cli.mjs whoami --json

# 登出
node dist/cli.mjs logout
```

**注意**: 本地开发环境 (`--spring.profiles.active=local`) 使用 Mock Auth，
不支持 Device Code Flow。需要使用已有的 token 或通过 Web UI 登录获取。

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

#### 3.2.1 explore - 浏览 Skills (交互式) / search / find / find-skills

**说明**: `explore` 是主命令，`search`、`find`、`find-skills` 都是别名，指向同一命令。

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| EXPLORE-01 | `explore` | 浏览最新 skills | 显示最新列表 |
| EXPLORE-02 | `explore <query>` | 搜索浏览 | 带搜索条件的浏览 |
| EXPLORE-03 | `search <query>` | 搜索 (explore 别名) | 同 explore |
| EXPLORE-04 | `find <query>` | 搜索 (explore 别名) | 同 explore |
| EXPLORE-05 | `explore -n <n>` | 限制结果数 | 只返回 n 条结果 |

**别名**: `explore` | `find` | `find-skills` | `search`

```bash
# 测试命令
node dist/cli.mjs explore                          # 浏览最新
node dist/cli.mjs explore openspec                # 搜索浏览
node dist/cli.mjs search openspec                  # 别名
node dist/cli.mjs find openspec                    # 别名
node dist/cli.mjs find-skills openspec             # 别名
node dist/cli.mjs explore openspec -n 5           # 限制结果
```

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| NS-01 | `namespaces` | 列出有权限的命名空间 | 显示 namespace 列表及角色 |

```bash
# 测试命令
node dist/cli.mjs namespaces
```

#### 3.2.2 inspect - 查看 Skill 详情

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| INSPECT-01 | `inspect <slug>` | 查看 skill 详情 | 显示完整 metadata (别名: info, view) |
| INSPECT-02 | `inspect <ns/slug>` | 指定命名空间 | 使用 ns/slug 格式 |
| INSPECT-03 | `inspect <slug> --namespace <ns>` | 指定命名空间 | 使用 --namespace 选项 |

```bash
# 测试命令
node dist/cli.mjs inspect openspec
node dist/cli.mjs inspect openspec --namespace vision2group  # 旧格式仍支持
node dist/cli.mjs inspect vision2group/openspec              # 新格式 ns/slug
```

#### 3.2.3 versions - 版本列表

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| VERS-01 | `versions <slug>` | 列出所有版本 | 显示版本列表 (别名: -) |
| VERS-02 | `versions <ns/slug>` | 指定命名空间 | 使用 ns/slug 格式 |

```bash
# 测试命令
node dist/cli.mjs versions openspec
node dist/cli.mjs versions vision2group/openspec
```

#### 3.2.4 resolve - 解析版本

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| RESOLVE-01 | `resolve <slug>` | 获取最新版本信息 | 显示版本 ID、fingerprint、download URL |
| RESOLVE-02 | `resolve <ns/slug>` | 指定命名空间 | 使用 ns/slug 格式 |
| RESOLVE-03 | `resolve <slug> --skill-version <ver>` | 指定版本 | 解析特定版本 |
| RESOLVE-04 | `resolve <slug> --tag <tag>` | 按 tag 解析 | 解析指定 tag (默认: latest) |
| RESOLVE-05 | `resolve <slug>` (单命名空间) | 只有一个匹配 | 直接解析，无选择器 |
| RESOLVE-06 | `resolve <slug>` (多命名空间) | 多个命名空间有同名 skill | 显示交互选择器 |
| RESOLVE-07 | `resolve <slug>` 取消选择 | 用户按 ESC | 显示 Cancelled |

```bash
# 测试命令
node dist/cli.mjs resolve openspec
node dist/cli.mjs resolve vision2group/openspec
node dist/cli.mjs resolve openspec --skill-version 20260407.195957
node dist/cli.mjs resolve openspec --tag beta
```

---

### 3.3 安装与管理

#### 3.3.1 install - 安装 Skills

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| INST-01 | `install <slug>` | 从 registry 安装 | 下载并安装，交互式选择命名空间、版本 |
| INST-02 | `install <ns/slug>` | 从指定命名空间安装 | 使用 namespace/slug 格式 |
| INST-03 | `install <source> -a` | 从 GitHub 安装 | 使用 --add 指定 GitHub 源 |
| INST-04 | `install <source> --source git` | 强制 git source | 明确使用 git |
| INST-05 | `install <source> --source local` | 强制 local source | 明确使用 local |
| INST-06 | `install <source> --source auto` | 自动检测 (默认) | 自动识别 source 类型 |
| INST-07 | `install <slug> --list` | 只列出不安装 | 显示可用版本信息 |
| INST-08 | `install <slug> --copy` | 复制模式安装 | 复制文件而非 symlink |
| INST-09 | `install <slug> --global` | 全局安装 | 安装到全局目录 |
| INST-10 | `install <slug> -y` | 跳过确认 | 直接安装 |
| INST-11 | `i <slug>` | 使用别名安装 | 同 install |
| INST-12 | `install <slug> --agent <agents...>` | 指定目标 agents | 交互式选择或指定 agents |
| INST-13 | `install <slug> --skill-version <ver>` | 指定版本 (非交互) | 跳过版本选择，直接安装指定版本 |
| INST-14 | `install <slug> --tag <tag>` | 指定 tag (非交互) | 解析 tag 到版本并安装 |
| INST-15 | `install <slug>` (多 namespace) | 多个 namespace 有同名 skill | 显示交互式 namespace 搜索 |

```bash
# Registry Source - ns/slug 格式 (默认命名空间为 global)
node dist/cli.mjs install openspec                          # global/openspec
node dist/cli.mjs install vision2group/openspec             # 指定命名空间

# Git Source (自动检测 owner/repo 或显式 --add)
node dist/cli.mjs install owner/repo
node dist/cli.mjs install https://github.com/owner/repo
node dist/cli.mjs install owner/repo -a

# Local Source (自动检测)
node dist/cli.mjs install ./my-skill
node dist/cli.mjs install /absolute/path/to/skill

# 强制 Source 类型
node dist/cli.mjs install openspec --source registry
node dist/cli.mjs install owner/repo --source git
node dist/cli.mjs install ./local-skill --source local

# 版本选择选项
node dist/cli.mjs install openspec --skill-version 1.0.0    # 指定版本 (非交互)
node dist/cli.mjs install openspec --tag beta                 # 指定 tag (非交互)
node dist/cli.mjs install openspec -y                        # 跳过所有交互确认

# 选项组合
node dist/cli.mjs install vision2group/openspec --copy --global
```

**Source 自动检测规则**:
- `owner/repo` 或 `https://github.com/...` → git
- `./path` 或 `/absolute/path` → local
- 其他 → registry (使用 ns/slug 格式解析)

**交互式安装流程** (非 `-y` 模式):
1. **Namespace 搜索** → 如果未指定 namespace 且有多个匹配，显示交互式搜索
2. **版本选择** → 选择要安装的版本 (tag 标注在版本后面)
3. 技能选择 → 如果有多个技能可用，交互式多选
4. Agent 选择 → 选择目标 agents (可多选)
5. Scope 选择 → Project vs Global
6. 安装模式 → symlink vs copy
7. 确认安装 → 显示摘要并确认

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
| DL-02 | `download <ns/slug>` | 指定命名空间 | 使用 ns/slug 格式 |
| DL-03 | `download <slug> --skill-version <ver>` | 指定版本 | 下载特定版本 |
| DL-04 | `download <slug> --tag <tag>` | 按 tag 下载 | 下载指定 tag |
| DL-05 | `download <slug> --output <dir>` | 指定输出目录 | 下载到该目录 |

```bash
# 测试命令
node dist/cli.mjs download openspec
node dist/cli.mjs download vision2group/openspec
node dist/cli.mjs download openspec --skill-version 20260407.195957
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
| PUB-04 | `publish [path] --tag <tags>` | 指定标签 | 单个或多个逗号分隔 |
| PUB-05 | `publish [path] --name <name>` | 指定显示名 | 使用该名称 |
| PUB-06 | `publish [path] --changelog <text>` | 添加更新日志 | 包含 changelog |

```bash
# 测试命令
node dist/cli.mjs publish ./my-skill -v 1.0.0
node dist/cli.mjs publish ./my-skill --namespace vision2group -v 1.0.1
node dist/cli.mjs publish ./my-skill --tag beta --name "My Skill"
node dist/cli.mjs publish ./my-skill --tag beta,stable
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

### 8.1 快速回归测试 (本地/远程通用)

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

### 8.2 远程服务器测试 (10.0.8.9)

```bash
#!/bin/bash
# remote-test.sh - 10.0.8.9 服务器测试

set -e

REGISTRY=http://10.0.8.9:8081
CLI="node dist/cli.mjs --registry $REGISTRY"

echo "=== SkillHub CLI 远程测试 ==="
echo "Registry: $REGISTRY"
echo ""

# Step 1: Device Code 获取 Token
echo "[1/4] 获取 Device Code..."
DEVICE_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/code" -H "Content-Type: application/json")
DEVICE_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.deviceCode')
USER_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.userCode')
VERIFICATION_URI=$(echo "$DEVICE_RESP" | jq -r '.data.verificationUri')

echo "User Code: $USER_CODE"
echo "URL: $VERIFICATION_URI"
echo ""

read -p "在浏览器中完成授权后按回车..."

# Step 2: 获取 Token
echo "[2/4] 获取 Token..."
TOKEN_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/token" \
  -H "Content-Type: application/json" \
  -d "{\"deviceCode\": \"$DEVICE_CODE\"}")
ACCESS_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.data.accessToken')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" = "null" ]; then
  echo "❌ Token 获取失败"
  echo "$TOKEN_RESP"
  exit 1
fi

echo "✅ Token 获取成功"
echo ""

# Step 3: CLI 认证
echo "[3/4] CLI 认证..."
$CLI login --token "$ACCESS_TOKEN"
echo ""

# Step 4: 测试命令
echo "[4/4] 运行测试..."
$CLI whoami
$CLI search openspec --json | jq '.data.skills[0]'
$CLI namespaces
$CLI inspect openspec
$CLI resolve openspec
$CLI versions openspec

echo ""
echo "=== 远程测试完成 ==="
```

### 8.3 完整 CLI 自动化测试

```bash
#!/bin/bash
# full-test.sh - 完整自动化测试

set -e

REGISTRY=${REGISTRY:-http://localhost:8080}
CLI="node dist/cli.mjs --registry $REGISTRY"

echo "=== SkillHub CLI 完整测试 ==="
echo "Registry: $REGISTRY"
echo ""

# 测试函数
test_cmd() {
  local name="$1"
  local cmd="$2"
  echo -n "[$name] "
  if eval "$cmd" > /dev/null 2>&1; then
    echo "✅"
    return 0
  else
    echo "❌"
    return 1
  fi
}

# 全局选项
test_cmd "--version" "$CLI --version | grep -q 1.0.0"
test_cmd "--help" "$CLI --help | grep -q Commands"

# 认证
test_cmd "login" "$CLI login --token test 2>&1 | grep -q Token" || true
test_cmd "whoami" "$CLI whoami 2>/dev/null"
test_cmd "logout" "$CLI logout 2>/dev/null" || true

# 搜索发现
test_cmd "search" "$CLI search openspec 2>/dev/null"
test_cmd "search --json" "$CLI search openspec --json 2>/dev/null | grep -q openspec"
test_cmd "namespaces" "$CLI namespaces 2>/dev/null"
test_cmd "inspect" "$CLI inspect openspec 2>/dev/null"
test_cmd "versions" "$CLI versions openspec 2>/dev/null"
test_cmd "resolve" "$CLI resolve openspec 2>/dev/null"

# 列表
test_cmd "list" "$CLI list 2>/dev/null"
test_cmd "list --global" "$CLI list --global 2>/dev/null"

# 评分
test_cmd "rating" "$CLI rating openspec 2>/dev/null"

# 我的
test_cmd "me stars" "$CLI me stars 2>/dev/null"
test_cmd "me skills" "$CLI me skills 2>/dev/null"

echo ""
echo "=== 测试完成 ==="
```

### 8.4 使用 Device Code 获取 Token 的标准流程

```bash
#!/bin/bash
# get-token.sh - 标准 Token 获取脚本

REGISTRY=${REGISTRY:-http://localhost:8080}

echo "=== 获取 Token ==="
echo "Registry: $REGISTRY"
echo ""

# 请求设备码
echo "Step 1: 请求设备码..."
DEVICE_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/code" \
  -H "Content-Type: application/json")

DEVICE_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.deviceCode // empty')
USER_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.userCode // empty')
VERIFICATION_URI=$(echo "$DEVICE_RESP" | jq -r '.data.verificationUri // empty')

if [ -z "$DEVICE_CODE" ]; then
  echo "❌ 请求失败"
  echo "$DEVICE_RESP"
  exit 1
fi

echo "✅ 请求成功"
echo ""
echo "=========================================="
echo "请在浏览器中打开以下链接完成授权:"
echo "$VERIFICATION_URI"
echo ""
echo "或直接访问: $REGISTRY/device-login"
echo "User Code: $USER_CODE"
echo "=========================================="
echo ""

read -p "完成授权后按回车继续..."

# 轮询获取 token
echo ""
echo "Step 2: 轮询获取 Token..."

for i in {1..30}; do
  TOKEN_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/token" \
    -H "Content-Type: application/json" \
    -d "{\"deviceCode\": \"$DEVICE_CODE\"}")
  
  ACCESS_TOKEN=$(echo "$TOKEN_RESP" | jq -r '.data.accessToken // empty')
  
  if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
    echo "✅ 获取成功!"
    echo ""
    echo "=========================================="
    echo "Token: $ACCESS_TOKEN"
    echo "=========================================="
    echo ""
    echo "使用方式:"
    echo "  node dist/cli.mjs --registry $REGISTRY login --token $ACCESS_TOKEN"
    exit 0
  fi
  
  echo "等待授权... ($i/30) 秒"
  sleep 2
done

echo "❌ 获取超时，请重试"
exit 1
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
# Token 登录
login --token <TOKEN>
logout
whoami
whoami --json

# Device Code Flow (获取 token)
# 1. curl -X POST "$REGISTRY/api/v1/auth/device/code"
# 2. 浏览器打开返回的 verificationUri 完成授权
# 3. curl -X POST "$REGISTRY/api/v1/auth/device/token" -d '{"deviceCode":"xxx"}'
```

### 搜索发现
```bash
# explore = find = find-skills = search (同一命令)
explore|find|find-skills|search [query] [-i] [-n <n>] [--json]
namespaces
inspect <ns/slug> [--namespace <ns>]      # 别名: info, view
versions <ns/slug>
resolve <ns/slug> [-v <ver>] [--tag <tag>] [--hash <hash>]
```

### 安装管理
```bash
# ns/slug 格式: install <namespace/slug> 或 install <slug> (默认 global)
install <ns/slug> [--copy] [--global] [-y] [--list] [--agent <agents...>] [--skill-version <ver>] [--tag <tag>]
add <source> [--skill <skills...>] [--agent <agents...>] [--global] [--copy]
list [--global|--project]                 # 别名: ls
uninstall <name> [--all] [--global] [--agent <agents...>] [-y]  # 别名: un
update [slug] [--all] [--global]           # 别名: up
check [--global] [--json]
download <ns/slug> [--skill-version <ver>] [--tag <tag>] [--output <dir>]
```

### 发布
```bash
publish [path] [--namespace <ns>] [-v <ver>] [--tag <tags>] [--name <name>] [--changelog <text>]
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
delete <slug> [--namespace <ns>] [-y]    # 别名: del, unpublish
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

---

## 十一、生产服务器测试 (10.0.8.9)

### 11.1 快速开始

```bash
# 1. 设置 registry
export REGISTRY=http://10.0.8.9:8081

# 2. 获取 token (使用 Device Code Flow)
./get-token.sh  # 或手动执行:
# curl -X POST "$REGISTRY/api/v1/auth/device/code"
# 浏览器授权后
# curl -X POST "$REGISTRY/api/v1/auth/device/token" -d '{"deviceCode":"xxx"}'

# 3. 登录 CLI
node dist/cli.mjs --registry $REGISTRY login --token <YOUR_TOKEN>

# 4. 测试
node dist/cli.mjs --registry $REGISTRY whoami
node dist/cli.mjs --registry $REGISTRY search openspec
node dist/cli.mjs --registry $REGISTRY install openspec
```

### 11.2 飞书 OAuth 测试

```bash
# 1. 获取 token (Device Code Flow)
REGISTRY=http://10.0.8.9:8081
DEVICE_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/code" -H "Content-Type: application/json")
echo "$DEVICE_RESP" | jq .

# 2. 在浏览器打开授权页面 (使用飞书登录)
# http://10.0.8.9:8081/device-login

# 3. 获取 token
read -p "飞书授权完成后按回车..."
TOKEN_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/token" \
  -H "Content-Type: application/json" \
  -d "{\"deviceCode\": \"$(echo $DEVICE_RESP | jq -r '.data.deviceCode')\"}")
echo "$TOKEN_RESP" | jq .

# 4. 提取并使用 token
TOKEN=$(echo "$TOKEN_RESP" | jq -r '.data.accessToken')
node dist/cli.mjs --registry $REGISTRY login --token "$TOKEN"
node dist/cli.mjs --registry $REGISTRY whoami
```

### 11.3 一键完整测试

```bash
#!/bin/bash
# one-click-test.sh - 一键测试 10.0.8.9

REGISTRY=http://10.0.8.9:8081
CLI="node dist/cli.mjs --registry $REGISTRY"

echo "=== SkillHub CLI 一键测试 (10.0.8.9) ==="
echo ""

# 获取 token
echo "[1/5] 获取 Device Code..."
DEVICE_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/code" -H "Content-Type: application/json")
DEVICE_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.deviceCode')
USER_CODE=$(echo "$DEVICE_RESP" | jq -r '.data.userCode')
echo "User Code: $USER_CODE"
echo "URL: $REGISTRY/device-login"

read -p "授权完成后按回车..."

echo "[2/5] 获取 Token..."
TOKEN_RESP=$(curl -s -X POST "$REGISTRY/api/v1/auth/device/token" \
  -H "Content-Type: application/json" \
  -d "{\"deviceCode\": \"$DEVICE_CODE\"}")
TOKEN=$(echo "$TOKEN_RESP" | jq -r '.data.accessToken')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "❌ Token 获取失败"
  exit 1
fi
echo "✅ Token: ${TOKEN:0:20}..."

echo "[3/5] CLI 登录..."
$CLI login --token "$TOKEN"

echo "[4/5] 测试命令..."
$CLI whoami
$CLI namespaces
$CLI search openspec --json | jq '.data.skills[0:2]'

echo "[5/5] 安装测试..."
$CLI resolve openspec --json | jq '.data'

echo ""
echo "=== 测试完成 ==="
```
