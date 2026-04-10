# SkillHub CLI 手动测试文档

> **版本**: v1.3.0  
> **更新**: 2026-04-09  
> **目标**: 完整测试 SkillHub CLI 所有命令和选项

---

## 测试前准备

### 1. 构建 CLI

```bash
cd skillhub-cli && pnpm install && pnpm build
```

### 2. 设置 Registry

```bash
# 使用测试环境 (推荐)
export SKILLHUB_REGISTRY=http://localhost:8081

# 或使用主开发环境
export SKILLHUB_REGISTRY=http://localhost:8080

# 或使用生产环境
export SKILLHUB_REGISTRY=https://skillhub.your-company.com
```

### 3. 启动测试环境 (推荐)

```bash
# 启动隔离测试环境
/mnt/cfs/chenbaowang/skillhub/scripts/start-cli-test-env.sh

# 设置 CLI
export SKILLHUB_REGISTRY=http://localhost:8081

# 停止环境
/mnt/cfs/chenbaowang/skillhub/scripts/stop-cli-test-env.sh
```

---

## 命令测试矩阵

### 认证命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-A01 | `node dist/cli.mjs --help` | 显示帮助 | 列出所有命令 |
| TC-A02 | `node dist/cli.mjs --version` | 显示版本 | 输出版本号 |
| TC-A03 | `node dist/cli.mjs login --token <TOKEN>` | Token 登录 | 成功保存 Token |
| TC-A04 | `node dist/cli.mjs whoami` | 查看当前用户 | 显示用户信息 |
| TC-A05 | `node dist/cli.mjs logout` | 登出 | 清除 Token |

### 搜索和发现命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-S01 | `node dist/cli.mjs search <keyword>` | 搜索 skills | 返回匹配列表 |
| TC-S02 | `node dist/cli.mjs search <keyword> --json` | JSON 输出 | 输出有效 JSON |
| TC-S03 | `node dist/cli.mjs search <keyword> --namespace <ns>` | 限定命名空间 | 只返回该 NS 结果 |
| TC-S04 | `node dist/cli.mjs namespaces` | 列出命名空间 | 显示用户有权限的 NS |
| TC-S05 | `node dist/cli.mjs inspect <slug> --namespace <ns>` | 查看 skill 详情 (info别名) | 显示完整信息 |
| TC-S06 | `node dist/cli.mjs view <slug>` | 查看 skill 详情 (inspect别名) | 同 inspect |
| TC-S07 | `node dist/cli.mjs inspect <slug>` | 跨 NS 搜索详情 | 搜索所有 NS |
| TC-S08 | `node dist/cli.mjs explore` | 浏览最新 skills | 显示最新列表 |
| TC-S09 | `node dist/cli.mjs explore --install` | 交互式选择安装 | 多选后安装 |
| TC-S10 | `node dist/cli.mjs versions <slug>` | 列出版本 | 显示所有版本 |
| TC-S11 | `node dist/cli.mjs resolve <slug>` | 获取最新版本 | 返回最新版本信息 |

### 安装命令 (核心)

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-I01 | `node dist/cli.mjs install <slug>` | 从 registry 安装 | 下载并安装 |
| TC-I02 | `node dist/cli.mjs install <slug> --namespace <ns>` | 从指定 NS 安装 | 从该 NS 下载 |
| TC-I03 | `node dist/cli.mjs install <slug> --agent <name>` | 安装到指定 agent | 安装到对应目录 |
| TC-I04 | `node dist/cli.mjs install <slug> --global` | 全局安装 | 安装到全局目录 |
| TC-I05 | `node dist/cli.mjs install <slug> --copy` | 复制模式 | 文件复制而非 symlink |
| TC-I06 | `node dist/cli.mjs install <slug> --list` | 只列出 skill | 不安装，只显示 |
| TC-I07 | `node dist/cli.mjs install <slug> -y` | 跳过确认 | 直接安装 |
| TC-I08 | `node dist/cli.mjs i <slug>` | 使用别名安装 | 同 install |
| TC-I09 | `node dist/cli.mjs install owner/repo` | 从 GitHub 安装 | 自动检测为 git source |
| TC-I10 | `node dist/cli.mjs install ./local-path` | 从本地路径安装 | 自动检测为 local source |
| TC-I11 | `node dist/cli.mjs add <source>` | 使用 add 命令 | 从 git/local 安装 |
| TC-I12 | `node dist/cli.mjs install owner/repo@skillname` | @skill 语法安装 | 只安装指定 skill |
| TC-I13 | `node dist/cli.mjs install owner/repo` (交互式) | 交互式多选 | 显示多选菜单 |

### 发布命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-P01 | `node dist/cli.mjs publish [path] -v <ver>` | 发布 skill | 成功发布到 registry |
| TC-P02 | `node dist/cli.mjs publish [path] --namespace <ns>` | 发布到指定 NS | 发布到该命名空间 |
| TC-P03 | `node dist/cli.mjs publish [path] --name <name>` | 指定显示名称 | 使用该名称 |
| TC-P04 | `node dist/cli.mjs publish [path] --tags <tags>` | 指定标签 | 添加标签 |
| TC-P05 | `node dist/cli.mjs sync [path]` | 批量发布 | 扫描并发布多个 |
| TC-P06 | `node dist/cli.mjs sync [path] --dry-run` | 预览模式 | 只显示不发布 |
| TC-P07 | `node dist/cli.mjs init [name]` | 创建 SKILL.md | 生成模板 |
| TC-P08 | `node dist/cli.mjs download <slug>` | 下载 skill 包 | 下载 zip |

### 本地管理命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-M01 | `node dist/cli.mjs list` | 列出已安装 | 显示 skills 列表 |
| TC-M02 | `node dist/cli.mjs list --global` | 列出全局 skills | 显示全局目录 |
| TC-M03 | `node dist/cli.mjs uninstall <name>` | 卸载 skill | 从本地移除 |
| TC-M04 | `node dist/cli.mjs uninstall --all` | 卸载全部 | 移除所有已安装 |
| TC-M05 | `node dist/cli.mjs uninstall <name> --yes` | 跳过确认 | 直接卸载 |
| TC-M06 | `node dist/cli.mjs uninstall <name> --global` | 卸载全局 skill | 从全局目录移除 |
| TC-M07 | `node dist/cli.mjs uninstall <name> --agent <name>` | 卸载指定 agent | 只从该 agent 移除 |
| TC-M08 | `node dist/cli.mjs un <name>` | 使用别名卸载 | 同 uninstall |
| TC-M09 | `node dist/cli.mjs update [slug]` | 更新 skill | 检查并更新 |
| TC-M10 | `node dist/cli.mjs update --all` | 更新全部 | 检查所有更新 |
| TC-M11 | `node dist/cli.mjs archive <slug>` | 归档 skill | 标记为归档 |
| TC-M12 | `node dist/cli.mjs check` | 检查已安装 skills | 对比 lock 文件 |
| TC-M13 | `node dist/cli.mjs check --json` | JSON 输出检查结果 | 输出有效 JSON |

### 社交功能命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-C01 | `node dist/cli.mjs star <slug>` | 加星 | 成功标记 |
| TC-C02 | `node dist/cli.mjs rating <slug>` | 查看评分 | 显示评分信息 |
| TC-C03 | `node dist/cli.mjs rate <slug> <score>` | 评分 | 提交评分 (1-5) |
| TC-C04 | `node dist/cli.mjs me skills` | 我的 skills | 列出我发布的 |
| TC-C05 | `node dist/cli.mjs me stars` | 我的 stars | 列出我加星的 |
| TC-C06 | `node dist/cli.mjs reviews` | 审核管理 | 显示审核队列 |
| TC-C07 | `node dist/cli.mjs notifications` | 通知列表 | 显示通知 |
| TC-C08 | `node dist/cli.mjs report <slug>` | 举报 skill | 提交举报 |

### 删除命令

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-D01 | `node dist/cli.mjs delete <slug>` | 删除 skill | 删除 (需 owner) |
| TC-D02 | `node dist/cli.mjs delete <slug> -v <ver>` | 删除指定版本 | 只删该版本 |
| TC-D03 | `node dist/cli.mjs unpublish <slug>` | unpublish alias | 同 delete |

---

## install 命令详解 (重点测试)

### 源码自动检测

```bash
# registry source (默认)
node dist/cli.mjs install openspec
node dist/cli.mjs install global--openspec

# git source (自动检测)
node dist/cli.mjs install vercel-labs/skills
node dist/cli.mjs install https://github.com/vercel-labs/skills

# local source (自动检测)
node dist/cli.mjs install ./my-skill
node dist/cli.mjs install /absolute/path/to/skill
```

### --source 选项

```bash
# 强制使用 registry
node dist/cli.mjs install openspec --source registry

# 强制使用 git
node dist/cli.mjs install owner/repo --source git

# 自动检测 (默认)
node dist/cli.mjs install openspec --source auto
```

### 完整选项

```bash
node dist/cli.mjs install <source> [选项]

选项:
  --source <type>      源码类型: auto, registry, git, local (默认: auto)
  --namespace <ns>    命名空间 (默认: global) - 仅用于 registry source
  -s, --skill <skills> 只安装指定 skills (多个用空格分隔)
  -a, --agent <agents> 目标 agents (多个用空格分隔)
  -g, --global         安装到全局目录
  -y, --yes            跳过所有确认
  --copy               复制而非 symlink
  --list               只列出可用 skills，不安装
```

---

## uninstall 命令详解 (重点测试)

### 基本用法

```bash
# 卸载单个 skill
node dist/cli.mjs uninstall openspec

# 卸载所有 skills
node dist/cli.mjs uninstall --all

# 使用别名
node dist/cli.mjs un openspec
```

### 选项

```bash
node dist/cli.mjs uninstall [name] [选项]

选项:
  --global           卸载全局目录的 skill
  -a, --agent <agents> 从指定 agents 卸载 (多个用空格分隔)
  -y, --yes          跳过确认提示
  --all              卸载所有已安装的 skills
```

### 示例

```bash
# 从所有 agents 卸载
node dist/cli.mjs uninstall openspec

# 只从 claude-code 卸载
node dist/cli.mjs uninstall openspec --agent claude-code

# 卸载全局 skill
node dist/cli.mjs uninstall openspec --global

# 跳过确认
node dist/cli.mjs uninstall openspec --yes

# 卸载全部 (需确认)
node dist/cli.mjs uninstall --all
```

---

## 错误处理测试

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-E01 | `node dist/cli.mjs install nonexistent` | 安装不存在的 skill | 错误: Skill not found |
| TC-E02 | `node dist/cli.mjs install ./nonexistent` | 安装不存在的本地路径 | 错误: Local path not found |
| TC-E03 | `node dist/cli.mjs uninstall nonexistent` | 卸载不存在的 skill | 提示: Skill not found |
| TC-E04 | `node dist/cli.mjs publish -v invalid` | 无效版本号 | 错误: Invalid semver |
| TC-E05 | `node dist/cli.mjs --registry invalid.com whoami` | 无效 registry | 连接错误 |
| TC-E06 | `node dist/cli.mjs install owner/repo@nonexistent` | @skill 指定不存在的 skill | 错误: No matching skills |

---

## 全局选项测试

| TC-ID | 命令 | 测试内容 | 预期结果 |
|-------|------|---------|---------|
| TC-G01 | `node dist/cli.mjs --registry <url> whoami` | 指定 registry | 使用该 URL |
| TC-G02 | `node dist/cli.mjs --json search test` | JSON 输出 | 输出 JSON |
| TC-G03 | `node dist/cli.mjs --no-input install test` | 禁用提示 | 不显示交互提示 |

---

## 回归测试脚本

```bash
#!/bin/bash
# regression-test.sh

set -e

echo "=== SkillHub CLI 回归测试 ==="

REGISTRY=${SKILLHUB_REGISTRY:-http://localhost:8081}
CLI="node dist/cli.mjs --registry $REGISTRY"

echo "[1/10] Help 命令..."
$CLI --help | grep -q "Commands:" && echo "✓ PASS" || echo "✗ FAIL"

echo "[2/10] 版本命令..."
$CLI --version | grep -q "1." && echo "✓ PASS" || echo "✗ FAIL"

echo "[3/10] 登录..."
$CLI login --token test-token && echo "✓ PASS" || echo "✗ FAIL"

echo "[4/10] 当前用户..."
$CLI whoami | grep -q "userId" && echo "✓ PASS" || echo "✗ FAIL"

echo "[5/10] 搜索..."
$CLI search openspec | grep -q "openspec" && echo "✓ PASS" || echo "✗ FAIL"

echo "[6/10] 列出命名空间..."
$CLI namespaces | grep -q "namespace" && echo "✓ PASS" || echo "✗ FAIL"

echo "[7/10] 安装 (registry)..."
$CLI install openspec && echo "✓ PASS" || echo "✗ FAIL"

echo "[8/10] 列出已安装..."
$CLI list | grep -q "skills" && echo "✓ PASS" || echo "✗ FAIL"

echo "[9/10] 卸载..."
$CLI uninstall openspec --yes && echo "✓ PASS" || echo "✗ FAIL"

echo "[10/10] 登出..."
$CLI logout && echo "✓ PASS" || echo "✗ FAIL"

echo "=== 回归测试完成 ==="
```

---

## 测试结果记录表

| 测试日期 | 测试者 | P0 通过率 | P1 通过率 | P2 通过率 | 问题数 | 状态 |
|---------|--------|----------|----------|----------|--------|------|
| | | | | | | |

---

## 已知问题

| # | 问题描述 | 严重性 | 状态 |
|---|---------|--------|------|
| 1 | symlink vs copy 模式可能混淆 | 中 | 待修复 |
| 2 | 部分 API routes 使用硬编码 | 低 | 已知 |

---

## 附录: 命令速查

```bash
# 认证
node dist/cli.mjs login --token <token>
node dist/cli.mjs logout
node dist/cli.mjs whoami

# 搜索发现
node dist/cli.mjs search <query> [--namespace <ns>] [--json]
node dist/cli.mjs namespaces
node dist/cli.mjs inspect <slug>  # info, view 是别名
node dist/cli.mjs explore [query] [--install | -i]  # find 是别名
node dist/cli.mjs versions <slug>
node dist/cli.mjs resolve <slug>

# 环境变量
DISABLE_TELEMETRY=1     # 禁用匿名遥测
DO_NOT_TRACK=1          # 同上
CI=true                 # CI 环境中自动禁用遥测

# 安装 (install + add 合并)
node dist/cli.mjs install <source> [--source auto|registry|git|local]
node dist/cli.mjs install <slug> [--namespace <ns>] [--skill <skills>] [--agent <agent>] [--global] [--copy] [--list]
node dist/cli.mjs install owner/repo@skillname  # @skill 语法
node dist/cli.mjs add <source>   # git/local 专用

# 发布
node dist/cli.mjs publish [path] -v <ver> [--namespace <ns>]
node dist/cli.mjs sync [path] [--namespace <ns>] [--dry-run]
node dist/cli.mjs init [name]
node dist/cli.mjs download <slug>

# 本地管理
node dist/cli.mjs list [--global]
node dist/cli.mjs uninstall [name] [--all] [--global] [--agent <agent>] [--yes]
node dist/cli.mjs update [slug] [--all] [--global]
node dist/cli.mjs check [--global] [--json]
node dist/cli.mjs archive <slug>

# 社交
node dist/cli.mjs star <slug>
node dist/cli.mjs rating <slug>
node dist/cli.mjs rate <slug> <score>
node dist/cli.mjs me skills
node dist/cli.mjs me stars
node dist/cli.mjs notifications
node dist/cli.mjs report <slug>

# 删除 (delete, del, unpublish 别名)
node dist/cli.mjs delete <slug> [-v <ver>]
node dist/cli.mjs unpublish <slug>

# 全局选项
node dist/cli.mjs --registry <url>
node dist/cli.mjs --no-input
node dist/cli.mjs --json
```
