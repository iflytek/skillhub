# 快速开始

## 一键部署

使用 curl 命令快速部署 SkillHub（包含所有服务：Web UI、Backend API、PostgreSQL、Redis、MinIO、Skill Scanner）：

```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up
```

**国内用户（阿里云镜像）：**
```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --aliyun
```

**自定义参数**：
```bash
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up \
  --version v0.2.0 \
  --home /opt/skillhub \
  --aliyun
```

**参数说明**：
| 参数 | 说明 | 示例 |
|------|------|------|
| `--version <tag>` | 指定版本 | `--version v0.2.0` |
| `--aliyun` | 使用阿里云镜像（国内推荐） | `--aliyun` |
| `--home <dir>` | 指定安装目录 | `--home /opt/skillhub` |
| `--no-scanner` | 禁用安全扫描服务 | `--no-scanner` |
| `--mirror-registry <url>` | 自定义镜像仓库 | `--mirror-registry registry.example.com` |

**其他命令**：
```bash
# 停止服务
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down

# 查看服务状态
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- ps

# 查看日志
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- logs

# 清理所有数据
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- clean
```

部署成功后访问：
- **Web UI**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **API 文档**: http://localhost:8080/swagger-ui.html
- **Skill Scanner**: http://localhost:8000

## 本地开发

如果需要从源码启动开发环境：

```bash
# 克隆仓库
git clone https://github.com/iflytek/skillhub.git
cd skillhub

# 启动所有服务（包含 Scanner）
make dev-all
```

### 国内开发者注意事项

如果 `make dev-all` 后端启动失败，常见原因：

1. **Maven 依赖下载超时**

   项目已内置阿里云镜像配置（`server/.mvn/settings.xml`），但 Maven 不会自动读取项目级配置。需要手动配置：

   ```bash
   # 方式一：复制到用户目录（推荐）
   mkdir -p ~/.m2
   cp server/.mvn/settings.xml ~/.m2/settings.xml

   # 方式二：每次构建时指定
   cd server && ./mvnw -s .mvn/settings.xml package
   ```

2. **Java 版本不匹配**

   SkillHub 要求 Java 21+：
   ```bash
   java -version
   ```

3. **端口冲突**

   检查 8080 端口是否被占用：
   ```bash
   lsof -i :8080
   ```

详细的错误排查步骤，请查看 [常见问题](faq.md#本地开发启动失败)。

## 登录系统

### 方式一：使用内置管理员账号

SkillHub 内置了一个超级管理员账号，可以直接登录：

- **用户名**：`admin`
- **密码**：`ChangeMe!2026`

> **安全提示**：生产环境部署后，请立即修改默认密码。

### 方式二：注册新账号

访问 http://localhost:3000/register 注册新账号。

### 方式三：使用 Mock 用户（仅本地开发）

本地开发时，可以使用 Mock 用户头快速登录：

```bash
# 普通用户
curl -H "X-Mock-User-Id: local-user" http://localhost:8080/api/v1/auth/me

# 超级管理员
curl -H "X-Mock-User-Id: local-admin" http://localhost:8080/api/v1/auth/me
```

在浏览器中，可以通过浏览器插件（如 ModHeader）添加 `X-Mock-User-Id` 请求头。

## 安装 CLI 工具

SkillHub 兼容 OpenClaw CLI，可以使用 `npx clawhub` 命令管理技能包：

```bash
# 配置 SkillHub 注册中心地址
export CLAWHUB_REGISTRY=http://localhost:8080

# 搜索技能包
npx clawhub search email

# 安装技能包
npx clawhub install my-skill

# 发布技能包
npx clawhub publish ./my-skill
```

## 发布第一个技能包

### 使用 CLI 工具发布（推荐）

1. **准备技能包**

创建一个简单的技能包目录：

```
my-skill/
├── skill.md          # 技能描述
├── package.json      # 元数据
└── scripts/          # 脚本文件
    └── main.py
```

2. **使用 CLI 发布**

```bash
# 配置注册中心
export CLAWHUB_REGISTRY=http://localhost:8080

# 发布到默认命名空间
npx clawhub publish ./my-skill

# 发布到指定命名空间
npx clawhub publish ./my-skill --namespace my-team
```

3. **等待安全扫描**

发布后，Skill Scanner 会自动扫描技能包，检测潜在的安全问题：
- 恶意代码检测
- 敏感信息泄露
- 依赖漏洞扫描
- 行为分析

扫描结果会显示在技能包详情页。

4. **等待审核**（如果命名空间开启了审核）

管理员会收到通知，审核通过后技能包正式发布。

### 使用 Web UI 发布

1. 访问 http://localhost:3000/dashboard/publish
2. 选择命名空间（如果没有，先创建一个）
3. 上传 zip 文件
4. 选择可见性（PUBLIC / PRIVATE / INTERNAL）
5. 点击「发布」

## 搜索和下载技能包

### 使用 CLI 工具

```bash
# 搜索技能包
npx clawhub search pdf

# 安装技能包
npx clawhub install pdf-parser

# 安装指定命名空间的技能包
npx clawhub install my-team--pdf-parser
```

### 使用 Web UI

1. 访问 http://localhost:3000/search
2. 输入关键词搜索
3. 点击技能包查看详情
4. 点击「下载」或复制安装命令

## 升级 SkillHub

使用 curl 命令升级到最新版本：

```bash
# 升级到最新版本
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- pull
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- down
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up

# 升级到指定版本
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --version v0.2.0
```

> **注意**：升级前建议备份数据库和对象存储。
