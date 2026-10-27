## Context

**Proposed / spec-only**；以下取舍都是推荐，不是维护者确认或现役功能说明。源码依据冻结于 `e88ebf57c2e9123ef9697ce9a08492fd7e18b7a8`，不以更新后的 main 替代本次基线。

| 依据 | 本稿保留的事实与限制 |
| --- | --- |
| [产品方向](../../../docs/00-product-direction.md)、[生命周期](../../../docs/14-skill-lifecycle.md) | 注册中心优先、`SKILL.md` 兼容；公共发布候选与 owner preview 不同。 |
| [解析器](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/metadata/SkillMetadataParser.java)、[包校验器](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/SkillPackageValidator.java) | 保留 frontmatter，有松散解析回退；新声明校验不能被回退抹掉。 |
| [SkillVersion](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersion.java)、[发布服务](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java) | `parsedMetadataJson` 保存版本 frontmatter；发布路径有所有者隔离与冲突检查。 |
| [V13](../../../server/skillhub-app/src/main/resources/db/migration/V13__skill_owner_uniqueness.sql)、[坐标解析](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillSlugResolutionService.java) | 唯一约束为 `(namespace_id, slug, owner_id)`；按入口使用 CURRENT_USER / PUBLISHED 偏好。 |
| [确认发布](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillReviewSubmitService.java)、[审核](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java) | 成功发布移动指针；审批还检查同名已发布记录并投影发布元数据。 |
| [解析](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillQueryService.java)、[治理](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillGovernanceService.java) | latest 读取既有指针；yank 由既有发布回退逻辑调整指针，不按 semver 排序。 |
| [生命周期投影](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillLifecycleProjectionService.java)、[Promotion](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java) | 投影区分发布与预览；Promotion 复制所选源版本的元数据。 |
| [搜索架构](../../../docs/04-search-architecture.md)、[索引事件](../../../server/skillhub-search/src/main/java/com/iflytek/skillhub/search/event/SearchIndexEventListener.java) | AFTER_COMMIT 异步重建，不保证发布与搜索原子可见。 |
| [发现接口](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/ResourceDiscoveryController.java)、[响应](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/ResourceSummaryResponse.java)、[Web 类型](../../../web/src/api/types.ts) | 现有 `resourceType` 为 SKILL / SUITE，不以包分类替换。 |
| [Skill 接口](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillController.java)、[CLI 客户端](../../../cli/src/clients/skillhub-client.ts) | 详情、版本与 resolve 是不同视角；TypeScript 的类型断言不是所有旧消费者宽容解码的证明。 |
| [Suite 规范](../add-skill-suites/specs/skill-suites/spec.md)、[成员解析](../../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/suite/SkillSuiteMemberStateResolver.java) | 成员锁定精确 SkillVersion，不因同名或 latest 变化重绑定。 |

## Goals / Non-Goals

只增加包分类、版本一致的展示和可选发现过滤；保留 Skill 聚合、文件布局、命令、权限与治理。Knowledge 表达以随包资料为主的组织意图，不证明内容无执行行为或可信。外部数据服务和新的执行/文件政策不在本稿范围。

## Decisions

### G1 — Declaration and historical compatibility

推荐唯一新声明位置：

```yaml
metadata:
  package-type: knowledge
```

新接收包仅接受精确字符串 `skill` / `knowledge`，不做大小写或空白纠正；键不存在则为 `skill`，不继承前一版本。显式 null、空串、未知字符串及非字符串值均拒绝并指出字段；其余自定义 metadata 沿用原支持，不顺带收紧格式。显式声明不能可靠解析时拒绝，不允许松散回退把无效声明视为未声明；这不是通用解析器重构。

推荐启用时冻结“已接收历史版本”的适用性，包含尚未发布的记录：这些版本一律维持 `skill`，即使原始键值恰为 `knowledge`、未知值或 null。读取、下载、后续原审核不得追溯失败；包原文和历史 frontmatter 不改写。启用后新接收或替换包内容才按新规则验证；历史作者需用新版本显式选择 Knowledge。

未来实现应保存**服务端可信的逐版本适用性及有效类型**，与原始 frontmatter 区分，不能只看未经验证的历史键、由包伪造适用性，或仅按复制后创建时间判断。具体存储/迁移不在此稿落地；上线前必须能可靠区分历史版本，否则 G1 未满足。Promotion 复制有效分类及其适用性，历史 `skill` 不因复制到新记录变成 Knowledge；新上传/rerelease 重新接收包时则执行新提交校验。

备选：追溯识别所有历史 `knowledge` 或拒绝历史未知值；不推荐，前者会抢占既有扩展键的含义，后者会破坏已接收包。键名及这项保守策略仍需 G1 批准。

### G2 — Version, identity and lifecycle

分类属于 SkillVersion，不属于可覆盖历史的 Skill 标签。已发布字节/声明不可原地改写；改变类型需新版本并走原发布、扫描及适用审核分支，不增加或豁免任何分支。

“公共当前类型”取**既有入口选出的 latest published 候选**的有效类型，不新算最大 semver，不选择“最近一个匹配类型的版本”。固定版本、tag、hash 先按既有规则解析，再取实际结果的类型；找不到、无权访问、未发布、被 yank 或未就绪时保留原拒绝/回退语义。

原始 `latestVersionId` 在 PRIVATE 上传时可能指向 UPLOADED/SCANNING；指针非空不是发布证明。现有详情投影可回退到已发布版本，批量公开摘要不做同样回退；本稿不修正这些差异。类型附着于各入口已选中的版本，不将非发布指针或 owner preview 变为公共候选。

授权预览按所选预览版本显示分类和原 `resolutionMode` / 状态，不能覆盖公共类型；无发布候选时不凭预览生成公共分类。yank 当前版本后使用既有 survivor 指针及其类型；无 survivor 则无公共分类/命中，不要求物理删除索引。Promotion 按所选源版本而非来源 latest 分类。

V13 允许不同 owner 的同名记录共存，不意味着可创建第二个同坐标已发布身份。类型不进入唯一键、坐标、冲突豁免、所有权或解析优先级；保留发布/审批的既有冲突检查及调用者偏好。既有 Suite 设计中的概念坐标不能替代 V13 的实际 Skill 唯一约束；本稿不修改 Suite 正文。

Knowledge 仍是 Skill：Skill/Suite 同 slug 的入口与命令不变；Suite 引用、成员资格与精确版本校验不变，不引入新的成员类型或动态重绑定。

### G3 — Opt-in exposure and discovery

推荐首批范围如下；这是待 G3 确认的端点边界，不是已存在的参数：

| 入口 | 推荐扩展 |
| --- | --- |
| `GET /api/v1/resources`、`GET /api/web/resources` | 可选 `packageType=skill` / `packageType=knowledge` 过滤；`include=packageType` 才扩展响应。 |
| SkillController 的详情、版本列表/详情、resolve（`/api/v1/skills` 与 `/api/web/skills`） | 仅以 `include=packageType` 扩展相应 Skill/版本对象；不增加类型过滤或重解析。 |
| 旧 Skill 搜索、CLI / ClawHub 兼容端点、下载及其他入口 | 保留原响应形状与行为；本片不扩展它们。 |

复用既有 [IncludeOptions](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/support/IncludeOptions.java) 的 `include` 扩展约定，不新增独立布尔参数；在上述入口支持 `packageType` 选项，不改变既有选项语义。未请求该选项时不增加任何新属性，包括资源发现默认响应，严格拒绝未知属性的旧消费者无需修改。请求该选项时，Skill 的有效候选返回可选字符串字段 `packageType: skill|knowledge`，原字段/枚举保持不变；Suite 不返回该字段。选项解析及未知选项错误沿用原约定。缓存键/表示协商必须区分 opt-in，不能把扩展表示返给未请求者。

资源摘要和 Skill 聚合详情根对象的分类取发布候选；版本列表项、版本详情对象及 resolve 取其实际版本。授权预览只在对应预览版本对象内标类型，不冒充已发布根分类；无对应版本则省略字段，不以 null/猜测值充数。新客户端遇字段缺失仍可按 Skill 使用，不将缺失当成服务端已确认某个分类。

过滤只约束当前发布的 Skill：省略 resourceType 时隐含 SKILL，显式 SUITE 配合过滤或未知 packageType 返回本地化 400。过滤不自动启用响应扩展。过滤发生在分页/计数前并保留既有权限过滤，不返回历史匹配或转向另一 owner。未过滤的旧搜索始终包含两个分类，但不新增 Suite 结果。

索引类型只是派生数据：返回分类必须与结果版本一致，过滤需校验当前发布候选，避免异步旧索引形成错误类型命中；允许沿用原刷新时序暂未搜到，不承诺立即收录或发布/搜索原子性。

备选：在所有旧响应直接加字段，依赖消费者忽略未知属性；不推荐，因为严格消费者可能失败。首片也不新增独立 Knowledge API/命令或修改 `resourceType`。

## Risks / Trade-offs

历史保守处理会让先前自填同名键的作者需重新发版；可信适用性保存和 opt-in 缓存隔离需实现验证，不能用“纯 additive”省略兼容验收。资料也可能含脚本、恶意指令或敏感信息；分类不是安全证据，不放宽现有扫描、审核、访问、下载就绪、隐藏/归档/yank、大小/扩展名与审计规则。

## Validation Strategy

本次仅做 OpenSpec 严格结构验证、status、diff 与本地链接/来源核对；不运行 Java、前端、CLI 构建或应用栈。行为场景是未来实现的验收建议，不是已通过的运行测试。实现和发行均需另行授权，并完成[待办](./tasks.md)中的 gate 与兼容回归。
