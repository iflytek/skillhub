# OpenClaw 集成指南

本文档说明如何配置 OpenClaw CLI 连接到 SkillHub 私有注册中心，实现技能的发布、搜索和下载。
> 不仅适用于 Openclaw，通过指定安装目录，可适用于其他的 CLI Coding Agent (Claude Code、OpenCode、Qcoder等) 或者 Agent 助手（Nanobot、CoPaw等）。

## 概述

SkillHub 提供了与 ClawHub 兼容的 API 层，使得 OpenClaw CLI 可以无缝对接私有注册中心。通过简单的配置，您可以：

- 🔍 搜索组织内的私有技能
- 📥 下载和安装技能包
- 📤 发布新技能到私有注册中心
- ⭐ 收藏和评分技能

## 快速开始

### 1. 配置 Registry 地址

在 OpenClaw 配置文件中设置 SkillHub 注册中心地址：

```bash
# 通过环境变量配置（临时）
export CLAWHUB_REGISTRY=https://skillhub.your-company.com
```

### 2. 登录认证（可选）

对于**全局命名空间（@global）的公开技能（PUBLIC）**，无需登录即可下载。对于以下情况需要认证：

- 团队命名空间的技能（无论可见性）
- NAMESPACE_ONLY 或 PRIVATE 技能
- 发布、收藏等写操作

```bash
# 使用 API Token 登录
npx clawhub login --token YOUR_API_TOKEN
# 如果使用 npm i -g clawhub 安装过 clawhub，文档中所有的 npx clawhub 命令都可以直接使用 clawhub 命令来执行

# 查看当前登录用户
npx clawhub whoami

# 退出当前用户登录
npx clawhub logout

# 查看帮助
npx clawhub --help
```

#### 获取 API Token

1. 登录 SkillHub Web UI
2. 进入 **个人设置 → API Tokens**
3. 点击 **创建新 Token**
4. 设置 Token 名称和权限范围
5. 复制生成的 Token

### 3. 搜索/浏览/查看技能

```bash
# 搜索，显示所有相关技能
npx clawhub search <skill-name>
# 搜索，结果显示前 5 个结果
npx clawhub search <skill-name> --limit 5  
# 显示 skill 详情
npx clawhub inspect <skill-name>
# 浏览最新技能
npx clawhub explore
npx clawhub explore --limit 20    # 前20个

# 示例
npx clawhub search find-skills
npx clawhub search find-skills --limit 5 
npx clawhub inspect find-skills

# 使用帮助
npx clawhub search --help
npx clawhub inspect --help
```

### 4. 安装/更新/卸载技能

```bash
# 安装
npx clawhub install <skill-name>
npx clawhub install <skill-name> --version <version number>   # 指定版本
npx clawhub install <skill-name> --force                      # 覆盖已有
npx clawhub --dir <install-path> install <skill-name>         # 指定目录

# 更新
npx clawhub update <skill-name>
npx clawhub update --all

# 卸载
npx clawhub uninstall <skill-name>

# 查看已安装 skills
npx clawhub list

# Claude Code 安装 Skill 示例
npx clawhub --dir ~/.claude/skills install find-skills
CLAWHUB_WORKDIR=~/.claude/skills npx clawhub install find-skills

# 使用帮助
npx clawhub install --help
npx clawhub update --help
npx clawhub uninstall --help
npx clawhub list --help
```

### 5. 发布技能

```bash
# 发布到 global 空间（需要相应权限）
npx clawhub publish ./my-skill --slug my-skill --name "My Skill" --version 1.0.0

# 发布到如 my-space 这样的团队空间
npx clawhub publish ./my-skill --slug my-space--my-skill --name "My Skill" --version 1.0.0
npx clawhub sync --all # 上传当前文件夹中所有的 skill

# 使用帮助
npx clawhub publish --help
npx clawhub sync --help
```

说明：
- `my-space--my-skill` 是兼容层 canonical slug，SkillHub 会将其解析为 namespace `my-space` 和 skill slug `my-skill`
- 为避免 CLI 展示与服务端最终坐标不一致，建议让 `SKILL.md` 中的 `name` 与 canonical slug 后半段保持一致

## API 端点说明

SkillHub 兼容层提供以下端点：

| 端点 | 方法 | 说明 | 认证要求 |
|------|------|------|----------|
| `/api/v1/whoami` | GET | 获取当前用户信息 | 必需 |
| `/api/v1/search` | GET | 搜索技能 | 可选 |
| `/api/v1/resolve` | GET | 解析技能版本 | 可选 |
| `/api/v1/download/{slug}` | GET | 下载技能（重定向） | 可选* |
| `/api/v1/download` | GET | 下载技能（查询参数） | 可选* |
| `/api/v1/skills/{slug}` | GET | 获取技能详情 | 可选 |
| `/api/v1/skills/{slug}/star` | POST | 收藏技能 | 必需 |
| `/api/v1/skills/{slug}/unstar` | DELETE | 取消收藏 | 必需 |
| `/api/v1/publish` | POST | 发布技能 | 必需 |

说明：
- 兼容层对外继续使用 “latest” 语义，但这里严格指向“最新已发布版本”
- 兼容层内部实现应从统一 lifecycle projection 的 `publishedVersion` 映射，而不是自行推导“当前版本”

\* 下载端点认证要求：
- **全局命名空间（@global）的 PUBLIC 技能**：无需认证
- **团队命名空间的所有技能**：需要认证
- **NAMESPACE_ONLY 和 PRIVATE 技能**：需要认证

## 技能可见性说明

SkillHub 支持三种技能可见性级别，下载权限规则如下：

### PUBLIC（公开）
- ✅ 任何人都可以搜索和查看
- ✅ **全局命名空间（@global）**：无需登录即可下载
- 🔒 **团队命名空间**：需要登录认证才能下载
- 📍 适用于组织内通用的、可公开分享的技能

### NAMESPACE_ONLY（命名空间内可见）
- ✅ 命名空间成员可以搜索和查看
- 🔒 需要登录且是命名空间成员才能下载
- 📍 适用于团队内部技能

### PRIVATE（私有）
- ✅ 仅所有者可以查看
- 🔒 需要登录且是所有者才能下载
- 📍 适用于个人开发中的技能

**重要说明**：
- 全局命名空间（`@global`）的 PUBLIC 技能支持匿名下载，便于组织内广泛分发
- 团队命名空间的所有技能（包括 PUBLIC）都需要认证，确保团队边界安全

## Canonical Slug 映射规则

SkillHub 内部使用 `@{namespace}/{skill}` 格式，但兼容层会自动转换为 ClawHub 风格的 canonical slug：

| SkillHub 内部坐标 | Canonical Slug | 说明 |
|-------------------|----------------|------|
| `@global/my-skill` | `my-skill` | 全局命名空间技能 |
| `@my-team/my-skill` | `my-team--my-skill` | 团队命名空间技能 |

OpenClaw CLI 使用 canonical slug 格式，SkillHub 会自动处理转换。

## 配置示例

### ClawHub CLI 环境变量配置

ClawHub CLI 通过环境变量配置：

```bash
# Registry 配置
export CLAWHUB_REGISTRY=https://skillhub.your-company.com

# 如需认证，先登录一次
clawhub login --token sk_your_api_token_here
```

### 环境变量配置

```bash
# Registry 配置
export CLAWHUB_REGISTRY=https://skillhub.your-company.com

# 可选：登录后再执行需要认证的命令
clawhub login --token sk_your_api_token_here
```

## 常见问题

### Q: 如何切换回公共 ClawHub？

```bash
# 取消设置自定义 Registry
unset CLAWHUB_REGISTRY

# ClawHub CLI 将使用默认的公共注册中心
```

### Q: 下载技能时提示 403 Forbidden？

可能原因：
1. 技能属于团队命名空间，需要登录
2. 技能是 NAMESPACE_ONLY 或 PRIVATE，需要登录
3. 您不是该命名空间的成员
4. API Token 已过期

解决方法：
```bash
# 设置新的 Token 并重新登录
clawhub login --token YOUR_NEW_TOKEN

# 测试连接
curl https://skillhub.your-company.com/api/v1/whoami \
  -H "Authorization: Bearer YOUR_NEW_TOKEN"
```

**提示**：全局命名空间（@global）的 PUBLIC 技能可以匿名下载，无需认证。

### Q: 如何查看我有权访问的所有技能？

```bash
# 搜索所有技能（会根据权限过滤）
npx clawhub search ""
```

### Q: 发布技能时提示权限不足？

- 发布到全局命名空间（`@global`）需要 `SUPER_ADMIN` 权限
- 发布到团队命名空间需要是该命名空间的 OWNER 或 ADMIN
- 联系管理员分配相应权限

### Q: 支持哪些 OpenClaw 版本？

SkillHub 兼容层设计兼容使用 ClawHub CLI 的工具。ClawHub CLI 通过 npm 分发：

```bash
# 安装 ClawHub CLI
npm install -g clawhub

# 或使用 npx 直接运行
npx clawhub install my-skill
```

如遇到兼容性问题，请提交 Issue。

## API 响应格式

### 搜索响应示例

```json
{
  "results": [
    {
      "slug": "my-team--email-sender",
      "name": "Email Sender",
      "description": "Send emails via SMTP",
      "author": {
        "handle": "user123",
        "displayName": "John Doe"
      },
      "version": "1.2.0",
      "downloadCount": 150,
      "starCount": 25,
      "createdAt": "2026-01-15T10:00:00Z",
      "updatedAt": "2026-03-10T14:30:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "limit": 20
}
```

### 版本解析响应示例

```json
{
  "slug": "my-skill",
  "version": "1.2.0",
  "downloadUrl": "/api/v1/skills/global/my-skill/versions/1.2.0/download"
}
```

### 发布响应示例

```json
{
  "id": "12345",
  "version": {
    "id": "67890"
  }
}
```

## 安全建议

1. **使用 HTTPS**：生产环境务必使用 HTTPS 连接
2. **Token 管理**：
   - 定期轮换 API Token
   - 不要在代码中硬编码 Token
   - 使用环境变量或密钥管理工具
3. **权限最小化**：为 Token 分配最小必需权限
4. **审计日志**：定期检查 SkillHub 审计日志

## 故障排查

### 启用调试日志

```bash
# 查看详细请求日志
DEBUG=clawhub:* npx clawhub search my-skill

# 或使用 verbose 模式
npx clawhub --verbose install my-skill
```

### 测试连接

```bash
# 测试 Registry 连接
curl https://skillhub.your-company.com/api/v1/whoami \
  -H "Authorization: Bearer YOUR_TOKEN"

# 测试搜索
curl "https://skillhub.your-company.com/api/v1/search?q=test"
```

## 进一步阅读

- [SkillHub API 设计文档](./06-api-design.md)
- [技能协议规范](./07-skill-protocol.md)
- [认证与授权](./03-authentication-design.md)
- [部署指南](./09-deployment.md)

## 支持

如有问题或建议：
- 📖 查看完整文档：https://zread.ai/iflytek/skillhub
- 💬 GitHub Discussions：https://github.com/iflytek/skillhub/discussions
- 🐛 提交 Issue：https://github.com/iflytek/skillhub/issues
