# 审核技能与套装

## 审核员的检查清单

1. 核对对象、提交者及目标版本；版本、头像资料和套装不能混用判断。
2. 读取本次提交的文件与变更，不仅阅读当前公开版本。新版审核期间，旧公开版本可能仍可下载。
3. 检查扫描状态及发现项。扫描执行失败、风险发现和未配置某项分析器分别处理；空报告不能当作安全结论。
4. 检查可见范围与受众，特别是套装每个成员的可用性和入口身份。成员通过审核不自动批准套装。
5. 批准前确认当前数据仍有效；拒绝时写明具体文件、问题及修订方向。点击失败时重新读取状态，避免对旧页面重复作出相反决定。
6. 审核后核对记录及发布结果；Agent 执行效果需另验。

### 作者如何跟踪和修订

普通 `PUBLIC / NAMESPACE_ONLY` 上传创建审核任务；`PRIVATE` 上传先保留，须确认发布或另行提审。在“我的提交”查看任务和意见；有权撤回者在待审任务选择「撤回」并确认。撤回是停止当前审核，不会删除已发布旧版本或撤销已批准的全局提升。被拒绝后根据意见修订，再通过当前版本页面提供的重新提交操作继续；不要把上传成功当成审核重开成功。


## 状态对象对照

先识别对象；同名枚举不表示同一流程。任务为 `PENDING` 时，版本可能仍在 `SCANNING`；不得用它替代版本的 `PENDING_REVIEW`。

### 审核任务状态

| 状态 | 判断依据 | 处理者 | 下一步 |
| --- | --- | --- | --- |
| `PENDING` | 任务等待审核决定 | 有权限的空间审核员或平台技能管理员 | 执行上方检查清单后决定 |
| `APPROVED` | 任务已批准 | 提交者 | 核对版本或资料生效结果 |
| `REJECTED` | 任务已拒绝并保留意见 | 提交者 | 修订后重提 |
| `SUPERSEDED` | 任务已被后续申请替代 | 提交者与审核员 | 查看当前申请，不处理旧任务 |

[ReviewTaskStatus](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewTaskStatus.java) 无 `WITHDRAWN`；技能版本撤回会删除待审任务并更新版本状态，见 [ReviewService](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java)。

### 技能版本状态

版本 `DRAFT` 不是创作 API 草稿对象。

| 状态 | 判断依据 | 处理者 | 下一步 |
| --- | --- | --- | --- |
| `DRAFT` | 版本尚处草稿阶段 | 技能所有者 | 完成准备，尚未发布 |
| `SCANNING` | 扫描尚在运行 | 扫描服务 | 等待报告后审核 |
| `SCAN_FAILED` | 扫描执行失败 | 部署方与技能所有者 | 定位错误后重试 |
| `UPLOADED` | 已上传但尚未发布 | 技能所有者 | 确认发布或提交审核 |
| `PENDING_REVIEW` | 版本等待人工决定 | 有权审核员 | 核对本次版本并作决定 |
| `PUBLISHED` | 版本已发布 | 使用者与技能所有者 | 按可见范围使用，执行效果另验 |
| `REJECTED` | 版本未获批准 | 技能所有者 | 修订并重新提交 |
| `YANKED` | 版本已撤下 | 技能所有者与使用者 | 检查可安装的替代版本 |

枚举以 [SkillVersionStatus](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java) 为准；操作入口见[发布与更新技能](./skill-publish.html)。

### 套装导入操作状态

| 状态 | 判断依据 | 处理者 | 下一步 |
| --- | --- | --- | --- |
| `RUNNING` | 导入正在执行 | 导入服务 | 跟踪原操作，不重复确认 |
| `WAITING_FOR_MEMBERS` | 等待成员版本就绪 | 导入服务与成员审核员 | 查看成员进度 |
| `BLOCKED_RETRYABLE` | 存在可重试阻塞 | 导入者 | 排障后重试原操作 |
| `REPREVIEW_REQUIRED` | 原预览不能继续确认 | 导入者 | 重新预览并核对差异 |
| `SUITE_DRAFT_CREATED` | 已创建技能套装草稿 | 套装所有者 | 管理页继续发布，尚未批准 |
| `CANCELLED` | 操作已取消 | 导入者 | 核对成员动作，并非全部回滚 |

依据 [SkillSuiteBundleOperationStatus](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/suite/bundle/SkillSuiteBundleOperationStatus.java)；恢复步骤见[套装 Bundle 导入](./suite-bundle.html)。

### 头像审核结果

头像复用技能审核申请，但结果分三个字段维度。

| 字段与取值 | 判断依据 | 处理者 | 下一步 |
| --- | --- | --- | --- |
| 候选 `status`：`PENDING`、`ACTIVE`、`REJECTED`、`SUPERSEDED`、`REMOVED` | 依次为待处理、正在使用、已拒绝、已替代、已移除 | 技能所有者与有权审核员 | 核对当前候选和使用中的头像 |
| `machineStatus`：`SKIPPED`、`PENDING`、`PROCESSING`、`PASSED`、`BLOCKED`、`ERROR` | 依次为跳过、待处理、执行中、机器允许、机器阻止、执行错误 | 机器审核服务与部署方 | `ERROR` 先排查服务；`BLOCKED` 不等同人工已拒绝 |
| `reviewStatus`：`NOT_REQUIRED`、`PENDING`、`APPROVED`、`REJECTED`、`APPEAL_PENDING` | 依次为无需人工、待审、已批准、已拒绝、申诉待审 | 有权审核员与技能所有者 | 读取意见及当前候选，按允许动作处理 |

依据 [SkillAvatarCandidate](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillAvatarCandidate.java)；响应的 `candidateStatus` 是候选状态；配置与重试见[技能头像与审核](./skill-avatars.html)。

## 审核入口

角色与自审限制见本页末尾；举报、提升申请另行处理。

![审核对象及职责](/diagrams/review-concept.png)

![审核任务列表](/screenshots/review-list.png)

## 使用步骤

**审核技能包**：

1. 访问 `/dashboard/reviews`
2. 查看待审核列表
3. 点击技能包查看详情：
   - 顶部核对技能名称、描述、状态、提交者和版本；GitHub 同步项显示独立的内容修订号、来源仓库与短 commit，完整安装用技术版本可在技术信息中查看。顶部的安全与许可证状态用于定位问题，点击后在“来源与安全”查看依据；没有结果、读取失败或许可证未识别都不能理解为通过
   - 在“技能内容”查看待审版本说明文档和文件树。说明文档在进入此页签后读取；选中单个文件后才读取其存储内容。如果存储对象已丢失，预览会提示失败
   - GitHub 更新项默认打开“变更对比”，逐文件核对新增、修改和删除；首次导入和普通技能默认打开“技能内容”。超过 64 KiB 或 1000 行时跳过逐行比较，超过 256 KiB 时不在线加载内容。当前版本文件可按原权限下载；已删除的旧文件还受基线接口 1 MiB 上限约束，超限时页面会说明无法从审核页下载
   - 在“来源与安全”核对固定 commit、许可证证据、完整扫描卡片和发现项；普通技能也可查看安全信息。扫描尚未完成时不能批准；GitHub 来源许可证依据缺失时按页面提示处理
   - 在“审核记录”查看提交历史和已完成的审核意见；该页签打开时才请求历史
   - 套件审核在内容区核对待审快照。快照加载失败时可重试，加载成功前“通过”按钮不可用

![普通发布路径中的扫描与审核](/diagrams/skill-publish-lifecycle-sketch.png)

本页的审核部分对应 PUBLIC / NAMESPACE_ONLY 分支：扫描完成后，版本等待审核决定。

4. 在页面底部固定的审核栏填写意见并做出决策；切换页签保留意见，切换到另一个审核对象时清空意见、驳回表单和未确认弹窗：
   - **批准**：技能包正式发布，开发者收到通知
   - **拒绝**：填写拒绝原因，开发者可修改后重新提交

5. 添加审核意见（可选）

**审核合规声明**：

如果待审核版本包含 `x-astron-compliance`，审核详情会展示版本级合规快照和差异摘要：

- 新增声明：待审版本新增了标准、控制项或证据。
- 删除声明：待审版本移除了上一发布版本已有的声明。
- 修改声明：标准、控制项标题或证据发生变化。
- 摘要变化：`complianceSnapshot.digest` 变化，表示规范化后的声明内容发生变化。

审核建议：

1. 确认声明是否与技能实际能力相关。例如安全响应技能声明 MITRE ATT&CK 技术编号时，应能在说明或证据文件中看到对应依据。
2. 点击差异项查看证据路径、外部链接和摘要，不只看声明标题。
3. 对删除或大范围修改的声明提高审核优先级，因为这会影响下游审计系统引用。
4. 如果证据缺失、路径不可访问、声明明显不匹配技能能力，建议拒绝并要求作者修正。

SkillHub 能保证的是结构正确、证据可追溯、版本快照不可变；不能替作者保证“真的合规”。

### 删除已提升到全局的技能

如果永久删除时提示「已提升的技能须通过审核撤销流程删除」，请从团队来源技能的详情页处理全局发布。隐藏、归档或撤回待审提交不会删除已经批准的全局技能；提交撤销申请也不代表撤销已完成。

1. 打开来源技能（例如 `@it/translate-document`），在「提升到全局」区域点击「申请撤销全局发布」，填写原因并确认。
2. 请平台 `SKILL_ADMIN` 或 `SUPER_ADMIN` 访问 `/dashboard/promotions` 的「撤销待审核」页签，核对来源技能和全局技能后点击「批准撤销」。`SKILL_ADMIN` 不能审核自己提交的申请；`SUPER_ADMIN` 可以。
3. 刷新来源技能详情，核对本次申请在「撤销记录」中显示「已撤销」（`APPROVED`）。「待审核」（`PENDING`）或「已拒绝」（`REJECTED`）表示该申请未完成撤销；活动全局关联仍存在时，普通删除仍会被阻止。历史申请的状态不能代替当前提升关系的检查。
4. 撤销批准后，关联的 `@global/translate-document` 及其所有全局版本会被删除，团队来源技能保留。需要删除来源技能时，再使用该技能的「永久删除」，仍需具备删除权限。

尚无待审撤销申请时，平台 `SKILL_ADMIN` 或 `SUPER_ADMIN` 也可以在来源技能详情页使用「管理员直接撤销」，确认后立即完成全局撤销并保留撤销记录。已有待审申请时，详情页只显示待审提示，请通过上述「撤销待审核」入口处理。请先核对确认框：该操作会删除整条全局技能，包括独立上传的全局版本、评价和统计；已安装到客户端的文件无法收回，原地址以后可能被其他技能使用。

如果撤销记录已经是 `APPROVED`，刷新后仍提示同一错误，请记录来源/全局技能 ID、撤销申请 ID、当前部署版本或镜像标签，以及删除请求的响应和请求 ID。还需确认撤销后是否又进行了新的全局提升。若当前界面没有撤销入口，先核对部署版本和数据库迁移是否与对应版本一致，再由维护者检查；不要直接修改数据库中的提升关系来绕过流程。

**处理举报**：

1. 访问 `/dashboard/reports`
2. 查看举报列表
3. 调查举报内容
4. 采取行动（归档技能包、警告用户等）

## API 接口

**提交审核**：
```http
POST /api/v1/reviews
Content-Type: application/json

{
  "skillVersionId": 123
}
```

**批准审核**：
```http
POST /api/v1/reviews/{id}/approve
Content-Type: application/json

{
  "comment": "Looks good! Approved."
}
```

**拒绝审核**：
```http
POST /api/v1/reviews/{id}/reject
Content-Type: application/json

{
  "comment": "Please fix the documentation and add more examples."
}
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| id | number | 审核任务 ID（路径参数） |
| comment | string | 审核意见（批准时可选；拒绝时应填写明确原因） |

**列出待审核任务**：
```http
GET /api/v1/reviews/pending?namespaceId=123&page=0&size=20
```

**列出我的提交**：
```http
GET /api/v1/reviews/my-submissions?page=0&size=20
```

**获取审核详情**：
```http
GET /api/v1/reviews/{id}
```

**获取审核中的技能包详情**：
```http
GET /api/v1/reviews/{id}/skill-detail
GET /api/v1/reviews/{id}/skill-detail?includeDocumentation=false
```

`includeDocumentation` 缺省为 `true`，保持原响应行为。设为 `false` 时仍返回技能、版本、文件元数据与说明文件路径，`documentationContent` 为空且不读取对象存储正文；审核详情首屏使用此模式，进入“技能内容”后再取正文。

**下载审核包**：
```http
GET /api/v1/reviews/{id}/download
```

**撤回审核**：
```http
POST /api/v1/reviews/{id}/withdraw
```

**举报技能包**：
```http
POST /api/v1/skills/{namespace}/{slug}/reports
Content-Type: application/json

{
  "reason": "INAPPROPRIATE_CONTENT",
  "details": "This skill contains malicious code"
}
```

## 注意事项

> **审核权限**：团队空间 Admin / Owner 可审核本空间；平台 SKILL_ADMIN / SUPER_ADMIN 具有平台审核权限。个人空间及本人提交的审核还受自审规则约束，不能仅凭“管理员”称呼判断。

- **审核时效**：建议在 24 小时内完成审核，避免阻塞开发者
- **审核记录**：所有审核操作都会记录到审计日志
- **合规审计**：合规声明以版本快照形式记录。审核通过或拒绝时，应结合差异摘要判断风险，但 Agent 执行 trace 不由 SkillHub 记录
- **审核意见**：拒绝时建议提供详细的改进建议
- **撤回限制**：只有待审核状态的技能包可以撤回
