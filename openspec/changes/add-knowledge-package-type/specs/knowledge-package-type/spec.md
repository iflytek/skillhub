## ADDED Requirements

### Requirement: Knowledge SHALL remain a Skill package classification

本稿为 **Proposed / spec-only**；所有 SHALL 均为待 G1–G3 同意的候选要求，非已接受策略；端点和兼容边界见[设计](../../design.md)。系统 SHALL 将 Knowledge 视为 SkillVersion 的包分类，保留 Skill 聚合、根 `SKILL.md`、坐标、安装目录与原命令。`resourceType` SHALL 保持 `SKILL` / `SUITE`，不得新增并列 KNOWLEDGE 资源或以分类改变 Suite 身份/成员资格。

#### Scenario: Skill and Suite share a slug
- **WHEN** 同一空间存在 Knowledge 分类的 `SKILL @global/guide` 和 `SUITE @global/guide`
- **THEN** 原 `skillhub install @global/guide` 只安装该 Skill，`skillhub suite install` 仍走 Suite 入口
- **AND** 类型感知响应仍以 `resourceType=SKILL` 表示前者，不把分类加入坐标

#### Scenario: Suite member stays pinned
- **WHEN** Suite 成员已固定某 SkillVersion，该 Skill 后续发布不同分类版本
- **THEN** 成员仍引用原精确版本，按该版本分类解释
- **AND** 原成员权限/可安装性校验不变，不新增成员类型或重绑定

### Requirement: New declarations SHALL be explicit and validated

系统 SHALL 在启用后新接收包时，仅将嵌套 `metadata.package-type` 的精确字符串 `skill` / `knowledge` 解释为分类；键缺失 SHALL 为 `skill`，与前一版本无关。显式未知、null、空串及非字符串值 SHALL 拒绝，错误指出字段；其他 metadata 支持 SHALL 保持不变。

#### Scenario: Omission does not inherit Knowledge
- **WHEN** v1 声明 knowledge，随后新提交 v2 不含该键
- **THEN** v2 的有效分类为 skill，v1 仍为 knowledge

#### Scenario: Invalid new declaration
- **WHEN** 新提交值为 `dataset`、`KNOWLEDGE`、` knowledge `、null、空串、布尔、数字、数组或对象
- **THEN** 系统拒绝本次包提交并指出 `metadata.package-type`，不生成有效新版本或默认为 skill

#### Scenario: Loose parsing must not erase a declaration
- **WHEN** 新提交含显式分类声明，但 YAML 歧义/错误或解析回退使其不能可靠验证
- **THEN** 系统拒绝该提交，不将声明丢失解释为缺省
- **AND** 不借此新增与分类无关的通用 metadata 限制

### Requirement: Historical metadata SHALL retain pre-feature semantics

系统 SHALL 保留启用前已接收版本的 `skill` 语义，不追溯解析保留键，不因历史键碰撞拒绝读取、下载或推进原审核。分类适用性 SHALL 来自可信服务端逐版本记录，与原始 frontmatter 分离；启用后新接收/替换包内容 SHALL 执行新提交校验。

#### Scenario: Historical reserved-key collision
- **WHEN** 启用前版本的原始 metadata 已使用 `package-type`，值为 knowledge、未知值、null 或其他类型
- **THEN** 该版本继续作为 skill 按原权限使用，原文及存储的 frontmatter 不被改写
- **AND** 不能因重新索引或新建复制记录而自动重分类或报新声明错误

#### Scenario: Previously received pending version
- **WHEN** 启用前已接收但尚未发布的版本，在启用后完成原审核
- **THEN** 仍按历史 skill 解释，不追溯拒绝其保留键
- **AND** 作者另交新包时则必须满足新声明规则，不能由包伪造历史适用性

### Requirement: Public classification SHALL follow the existing published candidate

系统 SHALL 在既有生命周期选定版本后附加其有效分类。公共当前分类 SHALL 使用该入口既有 latest published 候选，不按最大 semver、草稿或历史匹配重选；已发布版本 SHALL 不因后来版本而改变分类。改类型 SHALL 新建版本并走原发布及适用扫描/审核，不原地修改已发布声明或字节。

#### Scenario: Mixed historical types and latest changes
- **WHEN** 同一 Skill 依次发布 v1=skill、v2=knowledge、v3=skill，既有指针随每次成功发布移动
- **THEN** 公共分类依次为 skill、knowledge、skill
- **AND** 各历史版本保持原分类；提交/拒绝待审版本不替代公共分类

#### Scenario: Last approved version has lower semver
- **WHEN** v2.0.0=skill 已发布，随后 v1.5.0=knowledge 经既有发布流程成为指针目标
- **THEN** 公共分类为 knowledge，不重新选择版本字符串更大的 v2.0.0

#### Scenario: Authorized preview is not public latest
- **WHEN** 已发布 v1=skill，管理者可预览未发布 v2=knowledge
- **THEN** 授权预览的版本对象可展示 knowledge 及原状态/预览模式，公共分类仍为 skill
- **AND** 原始指针若因 PRIVATE 上传指向 UPLOADED/SCANNING，不以其非空证明已发布；沿用各入口已有拒绝/投影回退

#### Scenario: No published version exists
- **WHEN** 只有授权管理者可见的非发布版本
- **THEN** 不生成公共分类或搜索命中；管理者可见的预览类型不增加公开访问或安装权限

### Requirement: Resolution and yank SHALL preserve version-specific meaning

固定版本、tag、hash 的分类 SHALL 来自按既有规则实际解析的版本，不来自当前摘要。yank SHALL 沿用现有 fallback 选取与错误规则；分类不得使已撤回、无权限或未就绪版本可分发，也不得引入新的解析回退。

#### Scenario: Pinned resolution differs from discovery
- **WHEN** 当前 latest=knowledge，而客户端固定到仍可安装的历史 skill 版本
- **THEN** resolve 的分类为 skill，版本、ID、fingerprint 与下载目标仍是被固定的版本
- **AND** 公共摘要为 knowledge，不把该类型回填到旧版本

#### Scenario: Tag or hash selects an older version
- **WHEN** 既有 tag/hash 规则实际选中历史 knowledge 版本，而当前发布候选为 skill
- **THEN** 类型感知 resolve 返回 knowledge，不改 tag/hash 的原优先级、匹配/失败或回退行为

#### Scenario: Yank switches to a survivor
- **WHEN** 当前 knowledge 版本被 yank，既有治理逻辑将指针回退到 skill 版本
- **THEN** 公共分类随 survivor 变为 skill，固定到已 yank 版本仍按原规则拒绝分发

#### Scenario: Yank leaves no survivor
- **WHEN** yank 后不再有已发布 survivor
- **THEN** 沿用原 latest 不可用行为，不返回伪造 skill 分类或公开搜索命中
- **AND** 不要求物理删除现有搜索文档，也不改变原治理审计

#### Scenario: Promotion uses the selected source version
- **WHEN** Promotion 选择历史 knowledge 版本而非来源 latest=skill
- **THEN** 目标新版本复制该源版本有效类型/适用性并走原提升审核，不借来源 latest 改型

#### Scenario: Promotion preserves historical compatibility
- **WHEN** Promotion 复制启用前接收且原始键恰为 knowledge 的版本
- **THEN** 目标仍复制历史 skill 语义，不因目标记录新建而重新解释原始键

### Requirement: Package type SHALL NOT resolve coordinate collisions

系统 SHALL 保留 `(namespace_id, slug, owner_id)` 唯一约束、现有发布/审批冲突检查及坐标解析的调用者偏好。分类 SHALL 不作为新坐标、第二个发布身份、所有权覆盖、冲突豁免或同名选择的 tie-breaker；不新增跨类型唯一约束。

#### Scenario: Owner-isolated unpublished records coexist
- **WHEN** 两位 owner 已有同空间同 slug 的隔离未发布记录，分别声明 skill 与 knowledge
- **THEN** 不合并、迁移或删除记录，原 CURRENT_USER / PUBLISHED 解析偏好不变
- **AND** 不按分类选择另一 owner 或声称 namespace+slug 在数据库全局唯一

#### Scenario: Same owner changes classification through a version
- **WHEN** 同一 owner 在其现有坐标提交另一分类的新版本
- **THEN** 仍属于同一个 Skill ID，不创建第二个资源身份
- **AND** 若版本号已占用，分类不同也不豁免原版本冲突

#### Scenario: Knowledge cannot bypass a published name conflict
- **WHEN** 另一 owner 的同空间同 slug 记录已发布，当前用户尝试以另一分类发布或获批
- **THEN** 按该入口原有冲突/授权规则处理，不因声明 knowledge 获得第二个发布身份或覆盖原记录

### Requirement: Discovery SHALL stay inclusive and filter only current type

未带类型过滤的原 Skill 搜索 SHALL 同时包含符合原可见性与分发条件的 skill 和 knowledge，不新增 Suite 结果。新 `packageType` 过滤 SHALL 仅用于设计列出的资源发现入口，在分页/计数前约束当前发布 Skill 候选，不搜索历史版本；省略 resourceType 时限于 SKILL，与 SUITE 合用或未知过滤值 SHALL 返回本地化 400。

#### Scenario: Unmodified client searches and installs Knowledge
- **WHEN** 未修改的旧 CLI / 兼容客户端按原请求搜索可访问且可安装的 Knowledge 包
- **THEN** 包仍作为 Skill 返回，原 resolve/下载/安装命令与目录可继续使用
- **AND** 无需新命令、额外类型参数、客户端更新或放宽严格解码器

#### Scenario: Historical match does not satisfy the filter
- **WHEN** v1=knowledge、当前发布 v2=skill，资源发现请求 packageType=knowledge
- **THEN** 当前 Skill 不命中，不将其结果或安装目标替换成 v1
- **AND** packageType=skill 可按原条件命中，权限过滤及分页/计数语义保持一致

#### Scenario: Asynchronous index still has the old type
- **WHEN** 指针已从 knowledge 移到 skill，而异步索引尚未刷新
- **THEN** 不用旧索引类型产生 knowledge 过滤命中；结果分类须与实际结果版本一致
- **AND** 可沿用原索引延迟暂未收录，不承诺发布后立即可搜或事务级搜索原子性

### Requirement: Optional responses SHALL preserve strict legacy consumers

设计列出的扩展入口 SHALL 复用现有 `include` 约定，仅在请求 `include=packageType` 时为对应 Skill/版本对象附加可选 `packageType: skill|knowledge`；未请求该选项 SHALL 保持旧响应属性集合，选项解析及未知选项错误 SHALL 沿用原约定。过滤 SHALL 不自动启用扩展；缓存 SHALL 隔离 opt-in 表示。既有字段及 resourceType 枚举 SHALL 不变，Suite/无对应版本 SHALL 省略新字段。

#### Scenario: Strict decoder uses the unchanged response
- **WHEN** 严格拒绝未知属性的旧消费者原样调用资源发现、详情、版本或 resolve
- **THEN** 默认响应不新增 packageType，消费者无需修改 schema 或忽略额外字段
- **AND** CLI / ClawHub 等未纳入扩展的入口保持原形状，不误返缓存中的 opt-in 表示

#### Scenario: Type-aware caller requests additive metadata
- **WHEN** 新客户端显式 opt-in 查询可访问的 Knowledge Skill 版本或解析结果
- **THEN** 在原字段外返回 `packageType=knowledge`，若响应已有 resourceType 则仍为 SKILL
- **AND** 资源/详情根分类取发布候选，预览分类仅附着对应预览版本对象；无候选时省略，新客户端仍可走 Skill 兼容路径

### Requirement: Classification SHALL NOT bypass governance

Knowledge SHALL 复用全部适用的包校验、扫描、审核、所有者/空间/账号授权、可见性、下载就绪、hidden/归档/yank、限额及审计规则。分类 SHALL 不证明惰性或安全，不新增外部描述符、依赖求解、抓取/查询端点、凭据托管、服务端执行或改变可执行文件政策。

#### Scenario: Knowledge carries unsafe or restricted content
- **WHEN** 包声明 knowledge，但原策略会拒绝其文件、内容、权限、状态或分发请求
- **THEN** 原拒绝或审核/扫描要求保持有效，不因分类豁免任何策略
- **AND** 文档中的脚本、指令或外部链接不获得新的服务端执行、访问或抓取能力
