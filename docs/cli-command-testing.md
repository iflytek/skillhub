# SkillHub CLI 命令测试文档

> **注意**: CLI 尚未全局发布，所有测试需通过 `node skillhub-cli/dist/cli.mjs` 执行。
> 文档中使用 `skillhub` 代指 `node skillhub-cli/dist/cli.mjs`。

## 测试环境

| 项目 | 值 |
|---|---|
| CLI 版本 | 0.1.0 |
| Node.js | v24.14.1 |
| 后端 | Spring Boot on `http://localhost:8080` |
| JVM | OpenJDK 21.0.10+7-Ubuntu |
| Docker | Postgres/Redis/MinIO |
| 认证 | API Token (`sk_...`) |

## 前置准备

```bash
# 定义 CLI 路径别名（使用绝对路径）
CLI="node /mnt/cfs/chenbaowang/skillhub/skillhub-cli/dist/cli.mjs"

# 1. 启动后端
make dev-server-restart

# 2. 构建 CLI
cd skillhub-cli && pnpm install && pnpm run build && cd ..

# 3. 登录获取 Session Cookie
curl -s -X POST http://localhost:8080/api/v1/auth/local/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMe!2026"}' \
  -c /tmp/skillhub-cookies.txt

# 4. 创建 API Token（用于 CLI 认证）
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/tokens \
  -H "Content-Type: application/json" \
  -b /tmp/skillhub-cookies.txt \
  -d '{"name":"cli-test","expiresIn":"24h"}' \
  | node -e "const d=JSON.parse(require('fs').readFileSync('/dev/stdin','utf8')); console.log(d.data?.token||'')")

echo "Token: $TOKEN"

# 5. 登录 CLI
$CLI login --token "$TOKEN"
```

## 全局选项

所有命令共享以下全局选项：

| 选项 | 说明 | 默认值 |
|---|---|---|
| `-V, --version` | 输出版本号 | — |
| `--registry <url>` | 后端 API 地址 | `http://localhost:8080` |
| `--no-input` | 禁用交互提示 | — |
| `--json` | 以 JSON 格式输出 | — |
| `-h, --help` | 显示帮助 | — |

**用法示例**:
```bash
$CLI --registry http://192.168.1.100:8080 search test
$CLI --no-input publish ./my-skill --slug my-skill -v 1.0.0
```

---

## 命令测试清单

### A. 本地命令（无需后端）

#### A1. `skillhub --help`

```bash
$CLI --help
```

**预期输出**:
```
Usage: skillhub [options] [command]

CLI for SkillHub — publish, search, and manage agent skills

Options:
  -V, --version                  output the version number
  --registry <url>               Registry API base URL (default: "http://localhost:8080")
  --no-input                     Disable prompts
  inspect [options] <slug>       View skill metadata without installing
  explore [options]              Browse latest updated skills from the registry
  help [command]                 display help for command

Commands:
  login [options]                Authenticate with SkillHub registry
  logout                         Remove stored authentication token
  whoami                         Show current authenticated user
  publish [options] [path]       Publish a skill to SkillHub registry
  search [options] <query...>    Search for skills on SkillHub
  namespaces                     List namespaces you have access to
  add [options] <source>         Install skills from a repository or local path
  install|i [options] <slug>     Install a skill from SkillHub registry
  download [options] <slug>      Download a skill package to local directory
  list|ls [options]              List installed skills
  remove|rm [options] <name>     Remove an installed skill
  star [options] <slug>          Star a skill
  info|view [options] <slug>     Show skill details
  init [name]                    Create a new SKILL.md template
  me                             View your skills and stars
  reviews                        Manage skill reviews
  notifications|notif            Manage notifications
  delete|del [options] <slug>    Delete a skill you own
  versions [options] <slug>      List skill versions
  report [options] <slug>        Report a skill for review
  resolve [options] <slug>       Resolve the latest version of a skill
  rating [options] <slug>        View your rating for a skill
  rate [options] <slug> <score>  Rate a skill (1-5)
  archive [options] <slug>       Archive a skill you own
  help [command]                 display help for command
```

**验证点**: 24 个命令全部注册，全局选项完整

---

#### A2. `skillhub --version`

```bash
$CLI --version
```

**预期输出**: `0.1.0`

---

#### A3. `skillhub init [name]`

**创建 SKILL.md 模板**

```bash
# 3a. 默认名称（当前目录创建 SKILL.md）
# 注意: CLI 使用绝对路径，所以可以跨目录执行
cd /tmp && $CLI init
# 预期: Created SKILL.md at /tmp/SKILL.md
cat SKILL.md
```

生成的内容:
```markdown
---
name: my-skill
description: What this skill does and when to use it
---

# my-skill

Instructions for the agent to follow when this skill is activated.

## When to Use

Describe the scenarios where this skill should be used.

## Steps

1. First, do this
2. Then, do that
```

```bash
# 3b. 指定名称（创建子目录）
$CLI init my-awesome-skill
# 预期: Created SKILL.md at /tmp/my-awesome-skill/SKILL.md
cat my-awesome-skill/SKILL.md
# 内容中 name: my-awesome-skill
```

```bash
# 3c. 重复创建（应报错）
cd /tmp && $CLI init
# 预期: SKILL.md already exists (exit 1)
```

**验证点**:
- 默认名称为 `my-skill`
- 指定名称时创建子目录
- 重复创建时正确报错
- 生成的 YAML frontmatter 格式正确

---

#### A4. `skillhub list [options]`

**列出已安装技能**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--json` | 以 JSON 格式输出（全局选项） |

---

#### C3. `skillhub explore [options]`

**浏览最新更新的 skills**

| 选项 | 说明 |
|---|---|
| `-n, --limit <n>` | 最大结果数（默认: 20） |

```bash
# C3a. 浏览最新技能
$CLI explore
# 预期: 显示最新更新的技能列表

# C3b. 限制数量
$CLI explore --limit 10
# 预期: 仅显示前 10 个

# C3c. JSON 输出
$CLI explore --json
# 预期: JSON 格式输出
```

---

#### C4. `skillhub inspect [options] <slug>`

**查看 skill 元数据（不安装）**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--manifest` | 仅显示 manifest 内容 |

```bash
# C4a. 查看详情
$CLI inspect test-skill
# 预期: 显示 skill 的完整元数据

# C4b. 指定命名空间
$CLI inspect my-skill --namespace my-team

# C4c. 仅 manifest
$CLI inspect test-skill --manifest
# 预期: 仅显示 SKILL.md 的 manifest 内容

# C4d. JSON 输出
$CLI inspect test-skill --json
# 预期: JSON 格式输出
```

---

### D. 技能管理命令

```bash
# C2a. 存在的技能
$CLI info test-skill
# 预期:
# test-skill (test-skill)
# Namespace: global
# Version:   20260405.105607
# Author:    Admin
# Stars:     0  Downloads: 0
#
# A test skill created by CLI integration test
# Status:    ACTIVE

# C2b. 不存在的技能
$CLI info nonexistent-skill
# 预期: Skill not found: (exit 1)

# C2c. 帮助
$CLI info --help
```

**验证点**: 字段映射正确（summary, ownerDisplayName, starCount, downloadCount, publishedVersion）

---

### D. 技能管理命令

#### D1. `skillhub publish [options] [path]`

**发布技能到 SkillHub**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 目标命名空间（默认: global） |
| `--slug <slug>` | 技能标识符 |
| `-v, --ver <ver>` | 版本号（semver，如 1.0.0） |
| `--name <name>` | 显示名称 |
| `--changelog <text>` | 更新日志 |
| `--tags <tags>` | 逗号分隔的标签（默认: latest） |

```bash
# D1a. 完整发布
mkdir -p /tmp/test-publish-skill
cat > /tmp/test-publish-skill/SKILL.md << 'EOF'
---
name: test-publish
description: A skill for testing publish
---
# Test Publish
Testing the publish command.
EOF

$CLI publish /tmp/test-publish-skill \
  --slug test-publish \
  -v 1.0.0 \
  --namespace global \
  --name "Test Publish" \
  --changelog "Initial release" \
  --tags "latest,test"
# 预期:
# - Publishing test-publish@1.0.0 to global
# ✔ Published test-publish@1.0.0 (ok: true)
# Skill ID:  1
# Version:   1

# D1b. 最小发布（当前目录）
cd /tmp/test-publish-skill && $CLI publish --slug test-publish -v 1.0.1
# 预期: 发布成功

# D1c. 无效版本号
$CLI publish /tmp/test-publish-skill --slug test -v invalid
# 预期: --version must be a valid semver (e.g. 1.0.0) (exit 1)

# D1d. 无 SKILL.md 的目录
mkdir -p /tmp/empty-dir && $CLI publish /tmp/empty-dir --slug test -v 1.0.0
# 预期: SKILL.md not found in directory (exit 1)

# D1e. 帮助
$CLI publish --help
```

**验证点**: 发布成功返回 skillId/versionId，错误情况正确处理

---

#### D2. `skillhub versions [options] <slug>`

**列出技能版本**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--json` | 以 JSON 格式输出（全局选项） |

```bash
# D2a. 有版本的技能
$CLI versions test-publish
# 预期:
# v20260405.120939
#   PUBLISHED · Published: 2026-04-05T04:09:39.490019Z

# D2b. 不存在的技能
$CLI versions nonexistent
# 预期: Failed: (exit 1)

# D2c. 帮助
$CLI versions --help
```

---

#### D3. `skillhub resolve [options] <slug>`

**解析技能最新版本**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--json` | 以 JSON 格式输出（全局选项） |

```bash
# D3a. 存在的技能
$CLI resolve test-publish
# 预期:
# test-publish@20260405.120939
# Namespace:    global
# Version ID:   1
# Fingerprint:  sha256:...
# Matched:      null
# Download URL: /api/v1/skills/global/test-publish/versions/20260405.120939/download

# D3b. 帮助
$CLI resolve --help
```

---

#### D4. `skillhub delete|del [options] <slug>`

**删除自己拥有的技能**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `-y, --yes` | 跳过确认 |

```bash
# D4a. 交互确认（默认取消）
echo "n" | $CLI delete test-publish
# 预期: Delete test-publish from global? This cannot be undone. [y/N]

# D4b. 确认删除
echo "y" | $CLI delete test-publish
# 预期: 删除成功

# D4c. 帮助
$CLI delete --help
```

---

#### D5. `skillhub archive [options] <slug>`

**归档自己拥有的技能**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `-y, --yes` | 跳过确认 |

```bash
# D5a. 交互确认（默认取消）
echo "n" | $CLI archive test-publish
# 预期: Archive test-publish from global? [y/N]

# D5b. 帮助
$CLI archive --help
```

---

#### D6. `skillhub report [options] <slug>`

**举报技能**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--reason <text>` | 举报原因 |

```bash
# D6a. 带原因举报
echo "This skill contains inappropriate content" | $CLI report test-skill --reason "inappropriate content"
# 预期: 举报提交成功

# D6b. 帮助
$CLI report --help
```

---

### E. 交互命令

#### E1. `skillhub star [options] <slug>`

**收藏/取消收藏技能**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--unstar` | 取消收藏 |

```bash
# E1a. 收藏
$CLI star test-skill
# 预期: Starred test-skill

# E1b. 取消收藏
$CLI star test-skill --unstar
# 预期: Unstarred test-skill

# E1c. 帮助
$CLI star --help
```

---

#### E2. `skillhub rating [options] <slug>`

**查看自己对技能的评分**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |

```bash
# E2a. 未评分
$CLI rating test-skill
# 预期:
# test-skill: Not rated yet
# Use: skillhub rate <slug> <score>

# E2b. 已评分（rate 之后）
$CLI rating test-skill
# 预期:
# test-skill: ★★★★★ (5/5)
```

---

#### E3. `skillhub rate [options] <slug> <score>`

**给技能评分（1-5）**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |

```bash
# E3a. 正常评分
$CLI rate test-skill 5
# 预期: Rated test-skill: ★★★★★

# E3b. 无效分数（0）
$CLI rate test-skill 0
# 预期: Score must be between 1 and 5 (exit 1)

# E3c. 无效分数（6）
$CLI rate test-skill 6
# 预期: Score must be between 1 and 5 (exit 1)

# E3d. 非数字
$CLI rate test-skill abc
# 预期: Score must be between 1 and 5 (exit 1)

# E3e. 帮助
$CLI rate --help
```

---

### F. 安装部署命令

#### F1. `skillhub download [options] <slug>`

**下载技能包到本地目录**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `--output <dir>` | 输出目录（默认: 当前目录） |

```bash
# F1a. 下载到指定目录
$CLI download test-skill --output /tmp
# 预期: 下载 zip 到 /tmp/test-skill.zip

# F1b. 下载到当前目录
cd /tmp && $CLI download test-skill
# 预期: 下载 zip 到 ./test-skill.zip

# F1c. 不存在的技能
$CLI download nonexistent --output /tmp
# 预期: Download failed: (exit 1)

# F1d. 帮助
$CLI download --help
```

---

#### F2. `skillhub install|i [options] <slug>`

**从 SkillHub 安装技能**

| 选项 | 说明 |
|---|---|
| `--namespace <ns>` | 命名空间（默认: global） |
| `-y, --yes` | 跳过确认 |
| `-f, --force` | 强制重新安装（覆盖已有） |
| `--copy` | 复制文件而非符号链接 |

```bash
# F2a. 安装技能
$CLI install test-skill --yes
# 预期: 下载并安装到本地 agent

# F2b. 不存在的技能
$CLI install nonexistent --yes
# 预期: Install failed: (exit 1)

# F2c. 帮助
$CLI install --help
```

---

### G. 用户命令

#### G1. `skillhub me [subcommand]`

**查看自己的技能和收藏**

```bash
# G1a. 我的技能
$CLI me skills
# 预期:
# test-publish (test-publish)
#   global · v20260405.120939 · ⭐ 0 · ↓ 0 · ACTIVE

# G1b. 我的收藏
$CLI me stars
# 预期: 列出收藏的技能，或 "No starred skills."

# G1c. 帮助
$CLI me --help
```

---

#### G2. `skillhub namespaces`

**列出可访问的命名空间**

```bash
$CLI namespaces
# 预期:
# global (Global)
#   Role: admin · Status: ACTIVE
```

---

#### G3. `skillhub reviews [subcommand]`

**管理技能审核**

```bash
# G3a. 我的审核提交
$CLI reviews my
# 预期: 列出审核提交，或 "No review submissions."

# G3b. 帮助
$CLI reviews --help
```

---

#### G4. `skillhub notifications|notif [subcommand]`

**管理通知**

```bash
# G4a. 列出所有通知
$CLI notifications list
# 预期: 列出通知，或 "No notifications."

# G4b. 仅未读通知
$CLI notifications list --unread
# 预期: 仅显示未读，或 "No unread notifications."

# G4c. 标记已读
$CLI notifications read 1
# 预期: 标记通知 1 为已读

# G4d. 全部标记已读
$CLI notifications read-all
# 预期: 全部标记为已读

# G4e. 帮助
$CLI notifications --help
```

---

## 单元测试

```bash
cd skillhub-cli && pnpm test
```

**预期输出**:
```
✓ tests/skill-name.test.ts (5 tests) 3ms
✓ tests/source-parser.test.ts (6 tests) 27ms
✓ tests/api-client.test.ts (13 tests) 9ms
✓ tests/installer.test.ts (2 tests) 44ms
✓ tests/commands.test.ts (23 tests) 10ms

Test Files  5 passed (5)
Tests       49 passed (49)
Duration    2.68s
```

### 测试覆盖范围

| 测试文件 | 测试数 | 覆盖内容 |
|---|---|---|
| `api-client.test.ts` | 13 | ApiResponse 解包（Native/Compat）、HTTP 错误、Auth header、POST/PUT/DELETE |
| `commands.test.ts` | 23 | 所有 26 个命令注册验证、选项验证 |
| `source-parser.test.ts` | 6 | 本地路径、GitHub URL、shorthand、invalid、getCloneUrl |
| `installer.test.ts` | 2 | symlink/copy 模式安装 |
| `skill-name.test.ts` | 5 | namespace/slug 解析 |

---

## 完整集成测试脚本

```bash
#!/bin/bash
# 完整集成测试 - 一键运行所有命令测试
# 用法: ./scripts/test-cli-full.sh

set -e
CLI="node /mnt/cfs/chenbaowang/skillhub/skillhub-cli/dist/cli.mjs"
PASS=0; FAIL=0; TOTAL=0

assert() {
  local desc="$1" cmd="$2" expect="$3"
  TOTAL=$((TOTAL + 1))
  local output exit_code=0
  output=$(eval "$cmd" 2>&1) || exit_code=$?
  if [ "$expect" = "0" ] && [ "$exit_code" -eq 0 ]; then
    echo "  ✅ $desc"; PASS=$((PASS + 1))
  elif [ "$expect" = "1" ] && [ "$exit_code" -ne 0 ]; then
    echo "  ✅ $desc"; PASS=$((PASS + 1))
  elif [[ "$expect" == contains:* ]] && echo "$output" | grep -q "${expect#contains:}"; then
    echo "  ✅ $desc"; PASS=$((PASS + 1))
  else
    echo "  ❌ $desc (exit=$exit_code)"; echo "    $output" | head -2; FAIL=$((FAIL + 1))
  fi
}

echo "=== SkillHub CLI 完整集成测试 ==="

# 获取 Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/tokens \
  -H "Content-Type: application/json" \
  -b /tmp/skillhub-cookies.txt \
  -d '{"name":"cli-test","expiresIn":"24h"}' \
  | node -e "const d=JSON.parse(require('fs').readFileSync('/dev/stdin','utf8')); console.log(d.data?.token||'')")

# 登录
assert "login" "$CLI login --token $TOKEN" "contains:Authenticated"

# 本地命令
assert "help" "$CLI --help" "contains:login"
assert "version" "$CLI --version" "contains:0.1.0"
assert "init" "$CLI init" "0"
assert "init duplicate" "$CLI init" "1"
assert "list" "$CLI list" "0"
assert "remove not found" "$CLI remove nonexistent" "contains:not found"
assert "logout" "$CLI logout" "0"

# 认证
assert "whoami" "$CLI whoami" "contains:Handle"

# 搜索
assert "search" "$CLI search test" "0"

# 发布
mkdir -p /tmp/cli-test-skill && cat > /tmp/cli-test-skill/SKILL.md << 'EOF'
---
name: cli-test
description: CLI test skill
---
# CLI Test
EOF
assert "publish" "$CLI publish /tmp/cli-test-skill --slug cli-test -v 1.0.0" "contains:Published"

# 信息
assert "info" "$CLI info cli-test" "contains:Namespace"
assert "resolve" "$CLI resolve cli-test" "contains:Fingerprint"
assert "versions" "$CLI versions cli-test" "contains:PUBLISHED"

# 交互
assert "star" "$CLI star cli-test" "0"
assert "rating (before)" "$CLI rating cli-test" "contains:Not rated"
assert "rate" "$CLI rate cli-test 5" "contains:Rated"
assert "rating (after)" "$CLI rating cli-test" "contains:★★★★★"
assert "rate invalid" "$CLI rate cli-test 6" "1"

# 用户
assert "me skills" "$CLI me skills" "0"
assert "me stars" "$CLI me stars" "0"
assert "namespaces" "$CLI namespaces" "0"
assert "notifications" "$CLI notifications list" "0"
assert "reviews" "$CLI reviews my" "0"

# 安装
assert "download" "$CLI download cli-test --output /tmp" "0"
assert "install" "$CLI install cli-test --yes" "0"

# 清理
rm -rf /tmp/cli-test-skill /tmp/cli-test.zip

echo ""
echo "=== 结果: $PASS/$TOTAL 通过, $FAIL 失败 ==="
[ "$FAIL" -eq 0 ] && echo "✅ 全部通过" || echo "❌ 有 $FAIL 个失败"
```

---

## 已知问题

| 问题 | 状态 | 说明 |
|---|---|---|
| Native publish 500 | ✅ 已修复 | 后端增加 compat publish 方法 |
| API Token 无 /me/** 权限 | ✅ 已修复 | 后端增加 API Token 策略 |
| Star/Rate 403 | ✅ 已修复 | 后端增加 star/rating 策略 |
| `--version` 选项冲突 | ✅ 已修复 | publish 命令改为 `-v, --ver` |
| broken symlink 崩溃 | ✅ 已修复 | list 命令增加 try/catch |
| symlink vs copy 模式错误 | ✅ 已修复 | installer.ts 正确处理 |
| namespace/slug 解析错误 | ✅ 已修复 | parseSkillName() 工具函数 |
| `/api/v1/notifications` 权限 | 🔴 待修复 | 后端需添加 API Token 策略 |
| `/api/v1/reviews/submissions` 权限 | 🔴 待修复 | 后端需添加 API Token 策略 |
