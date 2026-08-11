# 审核与治理

## 功能描述

SkillHub 提供了完整的审核工作流，确保发布到注册中心的技能包符合团队规范。

审核机制分为两层：
- **命名空间审核**：团队管理员审核本命名空间的技能包
- **平台审核**：平台管理员审核推广到全局的技能包

![概念图](/diagrams/review-concept.png)

**审核流程**：

1. 开发者发布技能包 → 进入「待审核」状态
2. 管理员收到通知 → 查看技能包详情
3. 管理员决策 → 批准或拒绝
4. 批准后 → 技能包正式发布
5. 拒绝后 → 开发者收到反馈，可修改后重新提交

**审核状态**：

| 状态 | 说明 |
|------|------|
| **PENDING** | 待审核 |
| **APPROVED** | 已批准 |
| **REJECTED** | 已拒绝 |
| **WITHDRAWN** | 已撤回 |

**治理功能**：

- **审核工作流**：多级审核、批量审核
- **举报系统**：用户可以举报不当技能包
- **推广管理**：将命名空间技能包推���到全局
- **审计日志**：记录所有治理操作

## 使用场景

**场景一：命名空间管理员审核**

团队管理员审核成员提交的技能包，确保符合团队规范。

![操作截图](/screenshots/review-list.png)

**场景二：平台管理员审核推广**

平台管理员审核从命名空间推广到全局的技能包。

**场景三：举报处理**

用户举报不当技能包，管理员调查并处理。

**场景四：批量审核**

管理员批量批准多个符合规范的技能包。

## 使用步骤

**提交审核**：

1. 发布技能包时，系统自动创建审核任务
2. 开发者可以在「我的提交」中查看审核状态
3. 等待管理���审核

**审核技能包**：

1. 访问 `/dashboard/reviews`
2. 查看待审核列表
3. 点击技能包查看详情：
   - 查看元数据（名称、描述、版本）
   - 浏览文件列表
   - 在线查看文件内容
   - 下载完整包进行本地测试
   - 查看合规声明快照和相对上一发布版本的差异

![流程图](/diagrams/review-flow.png)

4. 做出决策：
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

**撤回审核**：

开发者发现问题，可以在审核通过前撤回提交：

1. 访问「我的提交」
2. 找到待审核的技能包
3. 点击「撤回」
4. 确认撤回

**处理举报**：

1. 访问 `/dashboard/reports`
2. 查看举报列表
3. 调查举报内容
4. 采取行动（归档技能包、警告用户等）

## API 接口

**提交审核**：
```bash
POST /api/v1/reviews
Content-Type: application/json

{
  "skillVersionId": "version-123"
}
```

**批准审核**：
```bash
POST /api/v1/reviews/{id}/approve
Content-Type: application/json

{
  "comment": "Looks good! Approved."
}
```

**拒绝审核**：
```bash
POST /api/v1/reviews/{id}/reject
Content-Type: application/json

{
  "comment": "Please fix the documentation and add more examples."
}
```

**参数说明**：
| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 审核任务 ID（路径参数） |
| comment | string | 审核意见（可选，最多 1000 字符） |

**列出待审核任务**：
```bash
GET /api/v1/reviews/pending?namespaceId=ns-123&page=0&size=20
```

**列出我的提交**：
```bash
GET /api/v1/reviews/my-submissions?page=0&size=20
```

**获取审核详情**：
```bash
GET /api/v1/reviews/{id}
```

**获取审核中的技能包详情**：
```bash
GET /api/v1/reviews/{id}/skill-detail
```

**下载审核包**：
```bash
GET /api/v1/reviews/{id}/download
```

**撤回审核**：
```bash
POST /api/v1/reviews/{id}/withdraw
```

**举报技能包**：
```bash
POST /api/v1/skills/{namespace}/{slug}/reports
Content-Type: application/json

{
  "reason": "INAPPROPRIATE_CONTENT",
  "details": "This skill contains malicious code"
}
```

## 注意事项

> **审核权限**：只有命名空间的 Admin 和 Owner 可以审核本命名空间的技能包。平台管理员可以审核所有技能包。

- **审核时效**：建议在 24 小时内完成审核，避免阻塞开发者
- **审核记录**：所有审核操作都会记录到审计日志
- **合规审计**：合规声明以版本快照形式记录。审核通过或拒绝时，应结合差异摘要判断风险，但 Agent 执行 trace 不由 SkillHub 记录
- **批量审核**：管理员可以批量批准多个技能包
- **审核意见**：拒绝时建议提供详细的改进建议
- **撤回限制**：只有待审核状态的技能包可以撤回
