# @motovis/skillhub CLI 人工测试文档

> **版本**: v1.0.0  
> **目标**: 生产环境全面测试验证  
> **覆盖**: 所有命令、选项、认证流程

---

## 测试前准备

### 1. 安装 CLI

```bash
# 从源码构建
cd skillhub-cli
npm install
npm run build

# 或安装已发布的 npm 包 (发布后)
npm i -g @motovis/skillhub
```

### 2. 环境变量 (可选)

```bash
# 使用自定义 registry
export SKILLHUB_REGISTRY=https://your-skillhub-server.com

# 禁用 prompts
export SKILLHUB_NO_INPUT=true
```

### 3. 测试环境

- **生产服务器**: https://skillhub.your-company.com
- **本地开发**: http://localhost:8080

---

## 测试用例矩阵

| 优先级 | 命令/功能 | 测试内容 | 预期结果 |
|--------|----------|---------|---------|
| P0 | 认证 | login/logout/whoami | 正常登录登出 |
| P0 | 搜索 | search 基本搜索 | 返回结果 |
| P0 | 安装 | install 从 registry 安装 | 成功安装到本地 |
| P0 | 发布 | publish 本地 skill | 成功发布到 registry |
| P1 | 命名空间 | --namespace 选项 | 跨命名空间操作 |
| P1 | 多个 Agents | install 到不同 agent | 支持 46 个 agents |
| P1 | 更新卸载 | update/uninstall | 本地 skill 管理 |
| P2 | JSON 输出 | --json 选项 | 机器可读输出 |
| P2 | Sync | sync 批量发布 | 扫描并发布多个 skills |
| P3 | 其他命令 | star, rating, review 等 | 社交功能正常 |

---

## P0 测试用例 (核心功能)

### TC-001: 帮助命令

```bash
# 测试命令
skillhub --help

# 验证
- 显示所有可用命令列表
- 显示全局选项: --registry, --no-input, --json
- 显示版本号
```

### TC-002: 版本命令

```bash
# 测试命令
skillhub --version

# 验证
- 输出: 1.0.0
```

### TC-003: 登录 (Token 认证)

```bash
# 测试命令
skillhub login --token YOUR_API_TOKEN

# 验证
- 成功消息
- Token 保存到 ~/.skillhub/config.json
```

### TC-004: 查看当前用户

```bash
# 测试命令 (需先登录)
skillhub whoami

# 验证
- 显示用户信息: userId, displayName, email
```

### TC-005: 登出

```bash
# 测试命令
skillhub logout

# 验证
- Token 从配置中移除
- 后续 whoami 应该失败或显示未认证
```

### TC-006: 搜索 Skills

```bash
# 测试命令
skillhub search openspec

# 验证
- 返回匹配的 skills 列表
- 每条显示: slug, displayName, namespace, version
```

### TC-007: 安装 Skill (默认 global 命名空间)

```bash
# 测试命令
skillhub install openspec

# 验证
- 从 global 命名空间下载
- 安装到当前目录的 .claude/skills/
- 显示成功消息
```

### TC-008: 发布 Skill

```bash
# 测试命令
cd /path/to/your-skill
skillhub publish -v 1.0.0

# 验证
- 发布到 global 命名空间
- 返回 skillId, namespace, version
- 在 skillhub web UI 可见
```

---

## P1 测试用例 (命名空间 & Agents)

### TC-101: 查看命名空间列表

```bash
# 测试命令
skillhub namespaces

# 验证
- 显示用户有权限的所有命名空间
- 显示每个命名空间的 role (Owner/Admin/Member)
```

### TC-102: 在指定命名空间搜索

```bash
# 测试命令
skillhub search openspec --namespace your-team

# 验证
- 只返回指定命名空间的结果
```

### TC-103: 从指定命名空间安装

```bash
# 测试命令
skillhub install your-team--openspec --namespace your-team

# 或使用 slug 格式
skillhub install openspec --namespace your-team

# 验证
- 从 your-team 命名空间安装
```

### TC-104: 发布到指定命名空间

```bash
# 测试命令
cd /path/to/your-skill
skillhub publish -v 1.0.0 --namespace your-team

# 验证
- 发布到 your-team 命名空间
- 需要有发布权限
```

### TC-105: 查看不同 Agents 的安装路径

```bash
# 测试命令
skillhub list

# 验证
- 显示所有已安装的 skills
- 显示安装在哪个 agent 目录
```

### TC-106: 安装 Skill 到指定 Agent

```bash
# 测试命令
skillhub install openspec --agent claude-code

# 验证
- 安装到 .claude/skills/ 而不是其他 agent
```

### TC-107: 全局安装 (所有 Agents)

```bash
# 测试命令
skillhub install openspec --global

# 验证
- 安装到所有检测到的 agents 的全局目录
```

---

## P2 测试用例 (高级功能)

### TC-201: JSON 输出格式

```bash
# 测试命令
skillhub search openspec --json

# 验证
- 输出有效的 JSON
- 包含 data/results 字段
```

### TC-202: 复制模式安装 (非 symlink)

```bash
# 测试命令
skillhub install openspec --copy

# 验证
- Skill 文件复制到本地
- 不是 symlink
- 可以独立使用
```

### TC-203: 跳过确认 prompts

```bash
# 测试命令
skillhub uninstall openspec --yes

# 验证
- 不显示确认提示
- 直接执行卸载
```

### TC-204: 批量更新 Skills

```bash
# 测试命令
skillhub update --all

# 验证
- 检查所有已安装 skills 的更新
- 显示可更新的列表
```

### TC-205: 更新特定 Skill

```bash
# 测试命令
skillhub update openspec

# 验证
- 只检查指定 skill 的更新
```

### TC-206: 扫描并发布 (Sync)

```bash
# 测试命令
skillhub sync /path/to/skills --namespace your-team

# 验证
- 扫描目录下所有包含 SKILL.md 的子目录
- 批量发布到指定命名空间
```

### TC-207: Sync 预览模式

```bash
# 测试命令
skillhub sync /path/to/skills --namespace your-team --dry-run

# 验证
- 只显示会发布什么，不实际发布
```

---

## P3 测试用例 (社交功能)

### TC-301: 查看 Skill 详情

```bash
# 测试命令
skillhub info openspec

# 或
skillhub view openspec

# 验证
- 显示完整的 skill 信息
- 包含描述、版本、作者、标签等
```

### TC-302: 查看 Skill 版本列表

```bash
# 测试命令
skillhub versions openspec

# 验证
- 列出所有版本
- 显示每个版本的发布时间
```

### TC-303: 下载 Skill 包

```bash
# 测试命令
skillhub download openspec -v 1.0.0

# 验证
- 下载 zip 包到当前目录
- 包含 SKILL.md 和其他文件
```

### TC-304: Star a Skill

```bash
# 测试命令
skillhub star openspec

# 验证
- 成功消息
- skill 被标记为 starred
```

### TC-305: 评价 Skill

```bash
# 测试命令
skillhub rate openspec 5

# 验证
- 成功消息
- 评价提交到 registry
```

### TC-306: 查看我的 Skills

```bash
# 测试命令
skillhub me skills

# 验证
- 显示我发布的所有 skills
```

### TC-307: 查看我的 Stars

```bash
# 测试命令
skillhub me stars

# 验证
- 显示我 starred 的 skills 列表
```

### TC-308: 删除 Skill

```bash
# 测试命令
skillhub delete openspec -v 1.0.0 --yes

# 验证
- 删除指定版本
- 需要是 owner 才能删除
```

### TC-309: Archive Skill

```bash
# 测试命令
skillhub archive openspec

# 验证
- Skill 被归档
- 不再出现在搜索结果中 (除非使用特殊 flag)
```

### TC-310: 查看通知

```bash
# 测试命令
skillhub notifications list

# 验证
- 显示通知列表
- 支持 mark as read
```

### TC-311: 解析 Skill (获取最新版本信息)

```bash
# 测试命令
skillhub resolve openspec

# 验证
- 返回最新版本号和下载 URL
```

### TC-312: 初始化 SKILL.md 模板

```bash
# 测试命令
skillhub init my-new-skill

# 验证
- 创建 my-new-skill/ 目录
- 生成 SKILL.md 模板文件
```

---

## 错误处理测试

### TC-401: 未认证时搜索

```bash
# 测试命令 (登出后)
skillhub search openspec

# 验证
- 应该仍然可以搜索 (公开内容)
```

### TC-402: 未认证时发布

```bash
# 测试命令 (登出后)
skillhub publish -v 1.0.0

# 验证
- 错误消息: 需要认证
```

### TC-403: 安装不存在的 Skill

```bash
# 测试命令
skillhub install non-existent-skill-xyz

# 验证
- 错误消息: Skill not found
```

### TC-404: 发布到无权限的命名空间

```bash
# 测试命令
skillhub publish -v 1.0.0 --namespace other-team

# 验证
- 错误消息: Permission denied
```

### TC-405: 版本格式错误

```bash
# 测试命令
skillhub publish --version invalid

# 验证
- 错误消息: Invalid semver
```

---

## 边界条件测试

### TC-501: 空搜索

```bash
# 测试命令
skillhub search ""

# 验证
- 返回错误或所有结果
```

### TC-502: 超长搜索词

```bash
# 测试命令
skillhub search "a b c d e f g h i j k l m n o p q r s t u v w x y z a b c d e f g h i j k l m n o p q r s t u v w x y z"

# 验证
- 正常处理
- 可能截断或返回空结果
```

### TC-503: 特殊字符在 namespace/slug 中

```bash
# 测试命令
skillhub install "team--skill-name"

# 验证
- 正确处理双破折号
```

### TC-504: 网络错误处理

```bash
# 测试命令 (离线)
skillhub search openspec

# 验证
- 错误消息: Network error / Connection refused
```

---

## Registry 配置测试

### TC-601: 使用自定义 Registry

```bash
# 测试命令
skillhub --registry https://custom-skillhub.example.com search openspec

# 验证
- 使用指定的 registry 而不是默认
```

### TC-602: Registry 不可达

```bash
# 测试命令
skillhub --registry https://nonexistent.example.com whoami

# 验证
- 清晰的错误消息
```

---

## 测试执行检查清单

### 测试前
- [ ] CLI 已正确安装 (`skillhub --version` 工作)
- [ ] 测试账号准备就绪 (token 可用)
- [ ] 测试用 namespace 有正确权限
- [ ] Registry 可访问

### 测试中
- [ ] 记录每个测试的实际输出
- [ ] 截图关键成功/失败界面
- [ ] 记录测试时间

### 测试后
- [ ] 所有 P0 测试通过
- [ ] P1 测试通过 (至少 80%)
- [ ] 已知问题已记录
- [ ] 性能问题已记录

---

## 回归测试 (发布前必跑)

```bash
#!/bin/bash
# regression-test.sh

set -e

echo "=== 回归测试开始 ==="

# P0: 核心功能
echo "[1/5] 测试 help..."
skillhub --help | grep -q "Commands:" && echo "✓ PASS" || echo "✗ FAIL"

echo "[2/5] 测试 login..."
skillhub login --token test-token && echo "✓ PASS" || echo "✗ FAIL"

echo "[3/5] 测试 search..."
skillhub search openspec | grep -q "openspec" && echo "✓ PASS" || echo "✗ FAIL"

echo "[4/5] 测试 install..."
skillhub install openspec && echo "✓ PASS" || echo "✗ FAIL"

echo "[5/5] 测试 logout..."
skillhub logout && echo "✓ PASS" || echo "✗ FAIL"

echo "=== 回归测试完成 ==="
```

---

## 测试报告模板

```markdown
## 测试报告

**日期**: YYYY-MM-DD
**测试者**: 
**CLI 版本**: 1.0.0
**Registry**: https://xxx

### 测试结果汇总

| 优先级 | 通过 | 失败 | 总计 |
|--------|------|------|------|
| P0 | X | X | X |
| P1 | X | X | X |
| P2 | X | X | X |
| P3 | X | X | X |

### P0 测试详情

| TC-ID | 测试内容 | 结果 | 备注 |
|-------|---------|------|------|
| TC-001 | help 命令 | PASS | |
| TC-002 | version 命令 | PASS | |
| ... | ... | ... | |

### 发现的问题

1. **问题描述**: 
   - 影响:
   - 严重程度: P0/P1/P2/P3
   - 复现步骤:
   - 期望行为:
   - 实际行为:

### 结论

[ ] 可以发布
[ ] 需要修复后发布
```

---

## 附录: 支持的 Agents (46个)

```
claude, claude-code, cursor, codex, opencode, github-copilot, cline,
windsurf, gemini, gemini-cli, roo, continue, openhands, qoder, trae,
kiro-cli, qwen-code, ollama, llama, codellama, wizardcoder, phi,
mistral, anthropic, cohere, ai21, stability, deepseek, local, jina,
perplexity, groq, fireworks, together, litellm, vllm, anyscale,
baseten, modal, replicate, bolt, goose, devin, swethe
```

---

## 附录: 命令速查

```bash
# 认证
skillhub login --token <token>
skillhub logout
skillhub whoami

# 搜索和发现
skillhub search <query> [--namespace <ns>] [--json]
skillhub namespaces
skillhub info <slug> [--namespace <ns>]
skillhub versions <slug> [--namespace <ns>]
skillhub resolve <slug> [--namespace <ns>]

# 安装
skillhub install <slug> [--namespace <ns>] [--agent <agent>] [--global] [--copy]
skillhub add <source> [--agent <agent>]

# 发布
skillhub publish [path] -v <ver> [--namespace <ns>] [--slug <slug>]

# 管理
skillhub update [slug] [--all]
skillhub uninstall <name> [--global] [--yes]
skillhub remove <name> [--global] [--yes]
skillhub delete <slug> [-v <ver>] [--yes]
skillhub archive <slug>

# 社交
skillhub star <slug>
skillhub rating <slug>
skillhub rate <slug> <score>
skillhub me skills
skillhub me stars
skillhub notifications list|read|read-all

# 工具
skillhub list [--global]
skillhub sync [path] [--namespace <ns>] [--dry-run]
skillhub init [name]
skillhub download <slug> [-v <ver>]

# 全局选项
skillhub --registry <url>
skillhub --no-input
skillhub --json
```
