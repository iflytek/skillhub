# Suite Bundle 批量导入

Suite Bundle 用一次 ZIP 或文件夹上传，为 Suite 创建首个版本，或基于一个已发布版本创建新版本。系统先展示成员差异，只有用户明确确认后才进入成员 Skill 的正常发布和审核流程。

## 归档结构

归档根目录必须且只能有一个 `SUITE.yaml`。每个携带包的成员使用独立目录，目录根部必须包含 `SKILL.md`。未声明的 `SKILL.md`、目录重叠、路径穿越、重复路径、超出大小或文件数限制都会阻止预览。

```text
SUITE.yaml
skills/
  intake/
    SKILL.md
  summarizer/
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
    入口技能负责接收和分派，摘要技能负责输出最终结果。
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
