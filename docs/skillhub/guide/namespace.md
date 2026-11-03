# 管理命名空间

本页说明团队命名空间的创建、成员维护和生命周期治理，适用于有相应权限的操作者。技能所有权、平台角色和命名空间角色分别管理；创建或管理命名空间不代表取得平台权限。`GLOBAL` 等非团队类型不适用本页的生命周期操作。

## 前置条件与职责

命名空间确定技能坐标中的归属，例如 `@team/example-skill`；该坐标仅为示例，不保证存在。命名空间 slug 创建后不可修改，显示名称可以在具备编辑权限时调整。

| 操作 | 所需身份 | 入口与判断依据 |
| --- | --- | --- |
| 创建团队命名空间 | 平台技能管理员（SKILL_ADMIN）或超级管理员（SUPER_ADMIN） | `/dashboard/namespaces` 的创建入口；普通成员不可创建 |
| 维护成员与资料 | 所有者（Owner）或空间管理员（Admin） | 命名空间处于 `ACTIVE`，详情页显示相应操作 |
| 发布技能 | 成员（Member）及具备发布权限的操作者 | 在发布页选择命名空间；仍受扫描、审核和技能维护权限约束 |
| 查看成员 | 已加入的成员 | 详情页“成员”页签；查看权限不包含成员修改权限 |

权限依据：[创建服务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/NamespacePortalCommandAppService.java)与[命名空间策略](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceAccessPolicy.java)。

## 创建并组织协作

1. 由有权创建者进入创建入口，填写显示名称、稳定 slug 和职责说明。创建成功后，创建者成为该命名空间所有者；此操作不新增平台角色。
2. 打开详情页“成员”，为已有账号添加成员或生成邀请。添加时选择 `MEMBER` 或 `ADMIN`；命名空间所有权通过独立转让入口处理，不使用普通角色选择框授予 `OWNER`。
3. 成员在发布页选择目标命名空间。面向成员共享时选择空间成员可见（NAMESPACE_ONLY）；私有（PRIVATE）不会对全体成员开放，公开（PUBLIC）也不要求读者先加入该命名空间。
4. 提交后，由有权审核人在命名空间审核入口处理，作者在“我的审核进度”跟踪结果。成员身份不能替代发布和审核完成状态。

![创建命名空间、加入成员并进入发布审核的职责顺序](/diagrams/namespace-membership-flow.svg)

图：创建、成员加入和发布是三个独立动作；邀请只授予成员资格，不直接发布技能。

创建与添加成员的请求示例如下，名称、slug 和用户标识均为占位值；程序化调用还须满足实例认证与防跨站请求规则。

```http
POST /api/v1/namespaces
Content-Type: application/json

{"displayName":"Example Team","slug":"example-team","description":"Shared authoring"}
```

```http
POST /api/v1/namespaces/{slug}/members
Content-Type: application/json

{"userId":"example-user","role":"MEMBER"}
```

## 邀请、角色调整与记录

在 `ACTIVE` 团队命名空间的“成员 → 邀请成员”创建链接，通过受控渠道分享。持链接者须先完成登录和现有准入，再核对目标、当前账号及 `MEMBER` 角色并确认加入；打开链接本身不建立成员关系。链接不限定邮箱或账号，不得在公开渠道发布。

| 项目 | 当前规则 | 生效与边界 |
| --- | --- | --- |
| 有效期与使用次数 | 创建后24小时，一名新成员使用一次 | 固定规则；到期或使用后不能再次加入 |
| 替换与作废 | 操作者确认后，旧链接立即失效 | 已加入成员保留成员资格 |
| 网页邀请记录 | 每页5条，可分页查询 | 网页固定展示；完整用户标识可通过悬停查看 |
| 历史查询分页 | 每页1–50条 | 请求参数控制，不返回邀请码或哈希 |
| 历史保留 | 命名空间存在期间保留 | 彻底删除命名空间时删除邀请业务记录；操作审计按原规则保留 |

链接采用 `/join#invite=邀请码`。创建人的同一账号、同一浏览器标签页暂存链接，刷新后重新打开弹窗，校验仍有效即可复制；换账号、标签页或设备不能依靠该暂存恢复。历史记录不提供原邀请码；原链接遗失时，须确认替换后创建新邀请。登录回跳不携带邀请 Token。

邀请历史显示创建人、时间、到期时间、状态和实际加入账号。改名或成员离开不改写当时加入事实；历史不代表当前成员资格。仅命名空间所有者及空间管理员可查看，冻结或归档时为只读。升级仅迁入可确认的当前邀请并标注旧记录，不推测更早历史。

角色调整时，在成员行选择修改角色，再使用操作栏保存；保存后重新核对角色。移除成员和转让命名空间所有权须分别确认，后者不转让其技能。邀请实现见[邀请服务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceInvitationService.java)及[历史展示](https://github.com/iflytek/skillhub/blob/main/web/src/features/namespace/invitation-history.tsx)。

## 生命周期治理

以下矩阵适用于 `TEAM`。服务端权限和依赖检查优先于角色名称。

| 命名空间状态 | 命名空间角色 | 可用操作 | 限制 |
| --- | --- | --- | --- |
| `ACTIVE` | 所有者 | 编辑、成员管理、所有权转让、冻结、归档、删除 | 删除还须无依赖 |
| `ACTIVE` | 空间管理员 | 编辑、成员管理、冻结 | 不可归档、删除或转让所有权 |
| `ACTIVE` | 成员 | 无上述治理操作 | 技能所有者身份不增加治理权限 |
| `FROZEN` | 所有者 | 解冻、归档、删除 | 成员管理只读；删除还须无依赖 |
| `FROZEN` | 空间管理员 | 解冻 | 不可归档或删除 |
| `FROZEN` | 成员 | 无上述治理操作 | 不可写入 |
| `ARCHIVED` | 所有者 | 恢复至 `ACTIVE`、删除 | 删除还须无依赖 |
| `ARCHIVED` | 空间管理员或成员 | 无上述治理操作 | 原编辑权限不包含恢复 |

| 命名空间状态 | 平台身份 | 平台专用治理入口 | 限制 |
| --- | --- | --- | --- |
| `ACTIVE` | 超级管理员 | 冻结、归档 | 不要求成员身份，无永久删除入口 |
| `FROZEN` | 超级管理员 | 解冻、归档 | 不要求成员身份 |
| `ARCHIVED` | 超级管理员 | 恢复至 `ACTIVE` | 不要求成员身份 |
| 任意状态 | 技能管理员或用户管理员 | 单靠平台角色无上述专用权限 | 另有成员角色时，按上一表判断 |

冻结用于暂停写入，不撤回客户端已安装文件；已有版本仍按访问规则读取。归档使命名空间退出搜索，并保留已有技能访问。永久删除仅由所有者发起，须没有技能、审核任务、指向该命名空间的提升申请及创作草稿；归档不会清除依赖。

依据：[命名空间服务](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java)与[平台治理入口](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminNamespaceController.java)。

## 完成标准与失败处理

创建后，列表应显示目标 slug、`ACTIVE` 和创建者的所有者角色；邀请接受后，成员列表应包含实际账号及 `MEMBER`。治理动作结束后重新读取详情，核对目标状态，例如解冻后为 `ACTIVE`。邀请记录显示已使用，不代表该账号当前仍为成员。

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 无创建入口 | 缺少平台创建角色 | 核对账号的平台角色及页面身份 | 联系有权创建者；不要反复注册账号 |
| 邀请无法加入 | 过期、已使用、作废或命名空间不可写 | 邀请弹窗核对状态；设置页核对命名空间状态 | 原操作者按需创建新邀请；不要重复使用旧链接 |
| 刷新后无法复制链接 | 标签页暂存不可用或链接已失效 | 原账号原标签页重开弹窗，读取有效性结果 | 先重试校验；原码不可恢复时确认替换，不要将历史记录当作邀请码 |
| 成员操作不可用 | 非所有者/空间管理员，或已冻结/归档 | 核对成员角色、状态及只读提示 | 由有权操作者恢复后再处理；不要修改浏览器请求绕过权限 |
| 永久删除被拒绝 | 存在四类依赖之一 | 按响应指出的对象，检查技能、审核、提升和草稿列表 | 先按各对象流程处理；不要直接删数据库记录 |

## 成员移除与维护边界

移除成员不会删除、迁移或转让其技能，已发布内容继续按原可见范围提供。账号正常的当前技能所有者仍可在“我的技能”查看和下载自己的内容，包括私有内容、未发布版本和历史版本；页面显示只读提示。仅凭该所有者身份不能上传、重新发布、改头像或可见范围、撤回、归档或删除；其他成员的受限内容不因该例外开放。

重新加入且仍为所有者时，按原规则恢复维护；停用账号不可操作，重新启用不恢复已移除的成员关系。已提交申请继续正常审核，不自动批准或撤回。空间及平台治理权限保持原规则，本版不提供技能转让、自助退出或永久注销功能；[所有者访问策略](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillOwnerAccessPolicy.java)不替代其他管理权限。

## 延伸阅读

- [发布与更新技能](./skill-publish.html)：按目标可见范围提交版本。
- [审核技能与套装](./review.html)：判断待审对象与处理结果。
- [账号与访问令牌](./account-access.html)：区分登录身份与接口凭据。
