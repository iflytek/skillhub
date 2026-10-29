# 通过 Bundle 导入技能套装

Suite Bundle 用一次 ZIP 或文件夹上传，为 Suite 创建首个版本，或基于一个已发布版本创建新版本。系统先展示成员差异，只有用户明确确认后才进入成员 Skill 的正常发布和审核流程。

安装和调用请先阅读[技能套装使用指南](./skill-suites)。本页面向批量导入内容的作者。`SUITE.yaml`（`kind: SkillSuiteBundle`）与套装设计中的 `suite.yaml`（`kind: SkillSuite`）不是同一种文件。

## 适合谁、准备什么

本页面向套装作者。只想安装使用的人应阅读[技能套装](/guide/skill-suites)，不需要制作 Bundle。导入前准备目标空间的写权限、完整成员清单、一个入口成员，以及每个 package 成员自己的有效 `SKILL.md`。reference 成员必须已经发布且你有权访问。

Bundle 用来创建成员版本并生成套装草稿，不会直接把技能安装进本地 Agent，也不会跳过成员审核。创建前确认套装坐标未被占用；更新前记录基准套装版本，不要把增量清单当成完整新版本。

## 导入中断与恢复

| 操作状态 | 含义与处理 |
| --- | --- |
| `RUNNING` | 成员发布处理中，查看成员行定位进度 |
| `WAITING_FOR_MEMBERS` | 等待成员完成发布流程；按成员链接查看审核状态 |
| `BLOCKED_RETRYABLE` | 查看具体受阻成员；扫描失败与成员发布失败使用不同重试提示，权限或空间状态问题需先恢复条件 |
| `REPREVIEW_REQUIRED` | 计划或基准发生变化，重新上传并预览，不能直接确认旧计划 |
| `SUITE_DRAFT_CREATED` | 成员阶段结束，打开生成的套装草稿继续审核与发布；这不是套装已发布 |
| `CANCELLED` | 本次套装创建已停止；已创建的成员版本及审核不会被自动撤销，仍可从成员链接跟踪 |

刷新页面后查看同一持久化操作，不要为了“继续”另建一份套装。完成后如无法打开草稿，保留操作 ID 并请管理员检查结果关联；不要把完成状态理解为所有结果均可访问。


## 归档结构

归档根目录必须且只能有一个 `SUITE.yaml`。每个携带包的成员使用独立目录，目录根部必须包含 `SKILL.md`。未声明的 `SKILL.md`、目录重叠、路径穿越、重复路径、超出大小或文件数限制都会阻止预览。

```text
SUITE.yaml
skills/
  intake/
    SKILL.md
```

```yaml
apiVersion: skillhub.iflytek.com/v1alpha1
kind: SkillSuiteBundle
metadata:
  namespace: global
  slug: clinical-workflow
spec:
  mode: CREATE
  version: 1.0.0
  displayName: 临床工作流
  summary: 串联病历接收与摘要生成
  overview: |
    接收结构化病历，完成字段校验后生成摘要。
    入口技能使用共享词典完成字段校验。
  visibility: PUBLIC
  entry: "@global/intake"
  members:
    - skill: "@global/intake"
      package:
        path: skills/intake
        visibility: PUBLIC
    - skill: "@global/shared-dictionary"
      reference:
        version: 2.3.1
```

成员只能选择一种来源：

- `package`：发布本次上传的 Skill 文件夹。新 Skill 必须明确填写 `visibility`；已有 Skill 只能由其所有者、Namespace 管理员或超级管理员更新。
- `reference`：精确引用市场中已发布且当前用户可访问的版本，不复制、不修改该 Skill。

## 创建与更新

创建 Suite 时使用 `mode: CREATE`，目标坐标必须不存在。更新时使用 `mode: UPDATE`，并增加 `baseVersion`；Manifest 必须描述新版本的完整成员顺序，基准成员未出现时会被标记为移除。

更新预览会显示 `ADDED`、`UPDATED`、`UNCHANGED` 和 `REMOVED`。成员顺序或 Entry 身份变化也属于更新；展示内容和成员均无变化的 Bundle 不能确认。`summary` 和 `overview` 在更新时可以继承基准版本，但最终值必须非空。

## 预览、确认与审核

1. 在“创建 Suite”或“创建新版本”页面选择“从本地导入”。
2. 选择 ZIP，或选择包含 `SUITE.yaml` 的根文件夹。
3. 检查每个成员的来源、目标版本、关系变化、发布动作、错误和警告。
4. 对每个存在警告的成员分别确认；更新时还要单独确认移除成员。
5. 确认后查看持久化操作进度。刷新页面后仍可恢复；权限撤销或 Namespace 冻结时可在恢复条件后重试。
6. 所有需要发布的成员都完成原有审核后，系统才创建 Suite 草稿；再按 Suite 审核流程提交。

预览不会创建 SkillVersion、审核任务或 SuiteVersion。确认请求使用 `Idempotency-Key` 防止网络重试重复创建操作，并重新检查权限、版本、引用和暂存文件摘要。

## 部署开关

Bundle 确认默认关闭。启用完整流程需要服务端同时允许 Bundle 确认和 Suite 审核写入：

```bash
SKILLHUB_SUITE_BUNDLE_CONFIRMATION_ENABLED=true
SKILLHUB_SUITE_REVIEW_WRITES_ENABLED=true
```

可以先保持确认关闭，仅验证上传和差异预览。私有化部署使用相同协议和本地 Registry 数据，不依赖 SaaS 地址。
