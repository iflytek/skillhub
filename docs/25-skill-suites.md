# Skill Suite 设计与使用

## 定位

Skill Suite 是一个有独立身份和版本的 Skill 集合。它只引用当前 SkillHub 中已经发布的精确
SkillVersion，不复制 Skill 文件，也不替成员重新执行扫描或审核。

Skill 与 Suite 的完整身份都包含资源类型，因此下列两个资源可以同时存在：

```text
SKILL @global/marketing
SUITE @global/marketing
```

原有 `skillhub install @global/marketing` 始终安装 Skill；Suite 必须使用
`skillhub suite install @global/marketing`，不会根据名称猜测类型。

## 版本和生命周期

SuiteVersion 固定成员的 Skill ID、SkillVersion ID、坐标、版本和 fingerprint。成员发布新版本不会
改变现有 SuiteVersion；调整成员、顺序、Entry Skill 或可见范围都需要创建新的 SuiteVersion。

```text
DRAFT -> PENDING_REVIEW -> PUBLISHED -> YANKED
                       \-> REJECTED -> DRAFT
```

- PRIVATE Suite 可以从 DRAFT 直接发布。
- PUBLIC 和 NAMESPACE_ONLY Suite 需要一次 Suite 级审核。
- Suite 没有可执行包，因此没有 SCANNING 或 SCAN_FAILED 状态。
- 隐藏、归档、下架或删除 Suite 不会改变任何成员 Skill。
- 成员失效后，已发布 SuiteVersion 保留历史快照并显示为不可安装，不会自动切换到成员最新版本。

## 成员与权限

一个 SuiteVersion 最多包含 100 个不同 Skill，并且必须明确选择其中一个普通成员作为 Entry Skill。
Entry Skill 仍是完整、可独立安装的 Skill。跨 Namespace 的 PUBLIC Skill 可以作为 Entry；非 PUBLIC
成员仍必须满足下表中的同 Namespace 受众约束。
v1 不支持嵌套 Suite、版本范围、外部 Registry 成员或条件成员。

Suite 的可见范围不能宽于成员：

| Suite 可见性 | 允许的成员 |
| --- | --- |
| PUBLIC | 仅 PUBLIC Skill |
| NAMESPACE_ONLY | PUBLIC，或同 Namespace 的 NAMESPACE_ONLY Skill |
| PRIVATE | PUBLIC，或同 Namespace 的 NAMESPACE_ONLY/PRIVATE Skill |

创建、提交、审核和安装时都会重新检查成员资格。成员被下架、隐藏、归档、收窄权限或硬删除后，
SuiteVersion 仍为 PUBLISHED，但安装计划会整体失败。硬删除只清空成员外键；坐标、版本和 fingerprint
快照继续用于历史展示和审计。

创作页面保存成员时会携带候选接口返回的精确 `skillVersionId`。服务端按 ID 读取版本，并校验坐标和
版本一致后再保存快照，不会按名称重新解析到另一个所有者的同名 Skill。

## `suite.yaml` 定义

`suite.yaml` 是可移植的 Suite 创作格式，不是上传到 Agent 的多 Skill ZIP：

```yaml
apiVersion: skillhub.iflytek.com/v1alpha1
kind: SkillSuite
metadata:
  namespace: global
  slug: superpowers
  version: 1.0.0
  displayName: Superpowers
spec:
  visibility: PUBLIC
  entrySkill: "@global/using-superpowers@1.0.0"
  members:
    - skill: "@global/using-superpowers"
      version: 1.0.0
    - skill: "@global/brainstorming"
      version: 2.1.0
```

当前版本通过 Web 编辑器或 Suite API 创建同一份定义；CLI v1 负责安装生命周期，尚不读取或发布
`suite.yaml`。保留该格式是为了后续增加 CLI 导入时不改变服务端领域模型。

## CLI 安装生命周期

```bash
skillhub suite install @global/superpowers --version 1.0.0
skillhub suite check @global/superpowers
skillhub suite upgrade @global/superpowers --check
skillhub suite upgrade @global/superpowers
skillhub suite remove @global/superpowers
```

安装、升级和卸载先获取当前 Suite 的本地操作锁，避免两个 CLI 进程基于同一份旧 inventory 并发
提交。安装随后解析精确计划并下载、校验全部成员，再按稳定顺序锁定目标目录并整体提交。提交中途
失败时，CLI 恢复本次替换的目录并保持安装前 inventory。卸载只移除当前 Suite 的来源；直接安装、
被其他 Suite 共享或已被本地修改的成员目录会保留。

CLI inventory 向后兼容旧记录。旧记录没有 `installedBy` 时按直接安装处理，不会在移除 Suite 时被
误删。新 CLI 在 Server 未声明 `skill-suite-v1` 能力时会明确停止 Suite 命令，普通 Skill 命令不受影响。

CLI 获取安装计划时会发送独立的 `Idempotency-Key`，遇到网络错误或 502/503/504 时使用同一个 key
重试一次。Server 按登录用户隔离该 key；匿名请求使用经过哈希的请求来源、客户端标识和 Suite 坐标
隔离，不保存原始身份字段。Server 为计划生成 `operationId`，在 24 小时窗口内避免重复记录 Suite
安装请求和审计。
安装计划本身不预增成员下载数；每个成员仍由原有 Skill 下载接口按实际请求计数。

本地 `local` profile 可直接运行 `make suite-smoke`。验证 release Compose 时必须使用真实管理员会话：

```bash
SMOKE_ADMIN_USERNAME=admin \
SMOKE_ADMIN_PASSWORD='<configured-password>' \
./scripts/suite-smoke-test.sh http://localhost:8080
```

脚本不会输出密码，并在结束时删除其创建的临时 Suite 和 Skill。

## API 与发现

- Suite 管理与详情：`/api/v1/suites/**`、`/api/web/suites/**`
- 当前用户可管理的 Suite：`/api/v1/me/suites`、`/api/web/me/suites`
- 类型化资源发现：`/api/v1/resources`、`/api/web/resources`
- 原有 Skill 搜索接口继续只返回 Skill。

类型化发现结果通过 `resourceType=SKILL|SUITE` 区分同名资源。Suite 详情返回固定版本、按顺序排列
的成员快照、Entry Skill、实时可安装状态和阻塞原因。普通 Skill 详情会列出当前用户可见、以该 Skill
作为 Entry 的最新已发布 SuiteVersion，并链接到完整 Suite；Skill 的独立安装能力保持不变。

## 部署顺序

数据库迁移会先把既有审核任务回填为 `SKILL_VERSION`，保留旧 Skill 专用列，并通过数据库触发器
把旧版 Server 新写入的 Skill 审核同步补全为类型化 subject。官方单实例
`compose.release.yml` 和本地开发 profile 已默认开启 Suite 审核写入，因为它们不会同时运行新旧 Server。

其他部署方式默认保持关闭。全新安装、单实例升级或停机升级可直接设置：

```bash
SKILLHUB_SUITE_REVIEW_WRITES_ENABLED=true
```

旧版与新版 Server 会同时运行的滚动升级，应在发布新版前保持：

```bash
SKILLHUB_SUITE_REVIEW_WRITES_ENABLED=false
```

用该配置完成所有 Server 实例升级；确认不再有旧版实例后，将其改为 `true` 并再次滚动重启。开关关闭
期间，现有 Skill 审核保持可用，Suite 草稿和 PRIVATE 直发不受影响，PUBLIC 与 NAMESPACE_ONLY
Suite 的新审核提交会被拒绝。Server 启动时会记录明确告警，避免门禁被长期遗忘。

## 日志与审计

Suite 创建、编辑、提交、审核、发布、下架、隐藏、恢复、归档和删除都会写审计记录。业务日志仅记录
Suite ID、SuiteVersion ID、actor ID 和 request ID 等定位字段，不记录成员内容、Token 或下载地址。
