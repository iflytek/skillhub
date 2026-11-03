# 收藏、评分与通知

本页说明已登录账号如何保存技能、提交使用反馈，以及管理站内通知。收藏、评分、订阅和通知互不替代；它们均不授予资源访问权限，也不执行技能。本页不说明平台审核操作或部署方邮件配置。

## 前置条件与对象区别

操作前，在同一注册中心核对当前账号及目标技能。收藏与评分要求有权访问技能；新增或修改文字评价还要求技能处于正常状态且有已发布版本。技能后续隐藏或权限变化，可能使原入口不可访问。

| 操作 | 保存的内容 | 适用目的 | 说明 |
| --- | --- | --- | --- |
| 收藏 | 当前账号与技能的收藏关系 | 在“我的收藏”重新定位技能 | 不安装技能，也不自动订阅更新。 |
| 评分与评价 | 当前账号对技能的星级与可选文字 | 记录使用反馈 | 文字评价不是发布审核意见。 |
| 订阅 | 当前账号与技能的更新订阅关系 | 接收符合投递条件的版本事件 | 收藏状态不代表已订阅。 |
| 通知 | 发送给当前账号的事件记录 | 跟踪发布、审核、提升或举报 | 已读不代表关联业务已处理。 |

权限依据：[收藏与评分入口](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillSocialAppService.java)、[文字评价规则](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillReviewAppService.java)。

## 收藏与提交使用反馈

1. 在技能详情执行收藏操作，等待请求结束并核对按钮状态；进入 `/dashboard/stars` 确认记录。再次取消收藏仅删除关系，不删除本地安装文件。需要筛选时，在搜索页启用“仅收藏”。
2. 在详情选择星级并等待提交结束，再核对个人评分。悬停预览不代表已保存；每个账号对同一技能只有一条评分，后续操作修改原评分，当前没有删除星级的接口。
3. 需要文字反馈时，使用“写评价”，提交星级与文字。公开列表展示可见评价；个人可以修改或清空自己的文字。清空文字保留星级，即使技能之后取消发布，仍可清空已有文字。
4. 被平台技能管理员（SKILL_ADMIN）或超级管理员（SUPER_ADMIN）隐藏的评价，在作者编辑后仍保持隐藏；恢复公开须由具有相应权限的平台角色操作。评分均值和下载量仅供选择参考，不能作为实际执行效果的证明。

以下为响应字段示例，技能 ID 必须替换为实例中的真实数值，不能直接复制 `{skillId}` 发起请求。

```http
GET /api/v1/skills/{skillId}/star
GET /api/v1/skills/{skillId}/rating
```

```json
{"data": {"score": 5, "rated": true}}
```

文字评价的读取、提交、清空分别使用 `GET`、`PUT`、`DELETE /api/v1/skills/{skillId}/reviews/me`；公开列表使用 `GET /api/v1/skills/{skillId}/reviews`。清空只移除文字，平台隐藏与恢复由对应角色在管理入口操作。

第一条接口的 `data` 为布尔值；上述 JSON 仅展示评分接口的 `data` 节选，不代表完整响应。写入接口返回空 `data`，不能据此读取评分时间。接口依据：[收藏](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillStarController.java)与[评分](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillRatingController.java)。

## 接收与处理站内通知

![业务事件、账号偏好与站内通知之间的关系](/diagrams/notification-delivery-flow.svg)

*图：事件按接收人和分类偏好形成站内记录；阅读通知后仍须在目标页面完成业务操作。*

1. 打开顶部通知入口或 `/notifications`，按分类和时间定位事件。通知标题说明事件，不替代目标对象当前状态。
2. 打开未读项，核对关联对象及版本；系统发起标记已读并跳转。审核申请仍须在审核详情中处理，不能将通知已读当作批准。
3. 在 `/settings/notifications` 按分类调整开关。保存后影响后续创建的通知，不删除已有记录；关闭期间未生成的通知，不因重新开启而补建。
4. 需要整理列表时，先确认业务处理结果，再标记已读或全部已读。删除按钮仅适用于已读通知；删除通知不撤销发布、审核或举报结果。

| 分类 | 触发事件 | 接收对象 | 说明 |
| --- | --- | --- | --- |
| 发布（PUBLISH） | 技能发布、订阅技能新版本或撤回已发布版本 | 满足事件条件的发布者、有效订阅者 | 订阅投递重新检查账号、命名空间与资源访问条件，并排除相应操作者。 |
| 审核（REVIEW） | 技能或个人资料提审，以及技能审核批准、拒绝 | 相应审核接收人或申请提交者 | 个人资料与技能审核不使用相同审核权限。 |
| 提升（PROMOTION） | 提升申请及批准、拒绝 | 平台技能审核接收人或申请提交者 | 通知不自动建立全局技能访问权。 |
| 举报（REPORT） | 举报提交与处理完成 | 平台技能审核接收人或举报人 | 历史记录不保证目标技能仍可查看。 |

上述为本页涉及的分类与代表事件，具体触发/接收条件以[事件监听器](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/NotificationEventListener.java)及[订阅接收条件](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SubscriptionRecipientEligibility.java)为准。接收人列表会在相应事件内去重，但通知服务不提供所有事件严格一次投递的全局保证。

## 数量、刷新与保留规则

| 配置或限制 | 默认值或范围 | 生效方式 | 说明 |
| --- | --- | --- | --- |
| 星级／评价长度 | 星级1–5；文字最多2000字符 | 提交时校验 | 越界请求被拒绝；仅修改星级不要求文字。 |
| 通知列表 | 网页每页20条；接口 `size` 范围1–100 | 分页请求时生效 | 不将当前一页数量视为全部记录数。 |
| 站内通知偏好 | 四类未保存时均开启，通道仅 `IN_APP` | 保存后影响后续通知创建 | 当前版本未提供邮件、短信或即时通信通知通道。 |
| 网页刷新 | 10秒轮询，窗口重新获得焦点时刷新 | 前端固定行为 | 不保证事件发生后立即显示。 |
| 已读记录保留 | `skillhub.notification.cleanup.read-retention-days=30` | 部署配置变更后重建或滚动注册中心 | 从 `createdAt` 计算，不从阅读时间计算。 |
| 未读记录保留 | `skillhub.notification.cleanup.unread-retention-days=90` | 部署配置变更后重建或滚动注册中心 | 从 `createdAt` 计算，不是永久保存。 |
| 清理调度 | 每日02:00 | 注册中心调度器执行 | 代码未固定时区，按实例调度时区；到期后由后续清理运行删除，不承诺精确到期时刻。 |

来源：[评价输入](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillReviewRequest.java)、[通知接口](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NotificationController.java)、[偏好服务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationPreferenceService.java)、[轮询](https://github.com/iflytek/skillhub/blob/main/web/src/features/notification/use-notifications.ts)及[清理任务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationCleanupTask.java)。

## 完成标准与失败处置

收藏应在个人列表出现，评分应在请求完成后显示个人已保存值；通知应显示正确分类与时间，标记操作完成后未读状态更新。目标页面业务状态须单独核对。

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 收藏或评分后未保存 | 登录失效、无访问权或请求失败 | 核对当前账号、详情访问及浏览器网络请求状态 | 登录后重新读取状态再操作；不要连续点击制造重复请求。 |
| 评价仅本人或平台角色可见 | 评价被隐藏 | 读取个人评价状态与处理原因，比较公开列表 | 按原因修订或联系相应平台角色；不要通过编辑规避隐藏状态。 |
| 未收到预期通知 | 分类关闭、接收条件不满足或事件尚在异步处理 | 核对通知设置、订阅关系及事件对应业务结果 | 先确认事件是否面向当前账号；不要仅为生成通知重复发布或提审。 |
| 未读数暂未变化 | 请求尚未完成或轮询尚未刷新 | 查看通知列表及未读数请求；重新聚焦窗口 | 等待请求完成后核对；不要把旧数字当作业务执行失败。 |
| 通知目标不可访问 | 资源删除、隐藏或权限已变化 | 核对目标路径、账号与当前命名空间成员资格 | 按当前权限处理；不要将历史通知作为访问凭证。 |
| 历史通知已消失 | 手工删除或达到清理条件 | 比较创建时间、已读状态及实例保留配置 | 通过业务对象查询结果；不要将通知列表用作永久审计档案。 |

## 边界与后续

旧 `GET /api/v1/notifications/sse` 已移除，自建客户端须轮询 `GET /api/v1/notifications` 和 `GET /api/v1/notifications/unread-count`。通知处理是异步的，刷新周期不是投递时限承诺。发布后仍需[管理版本](./skill-publish.html)，审核通知须进入[审核流程](./review.html)处理；账号与命名空间变更按[账号](./account-access.html)和[命名空间](./namespace.html)规则生效。
