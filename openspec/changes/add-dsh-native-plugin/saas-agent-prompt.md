# SaaS Agent 研发交接 Prompt：适配 SkillHub 原生 DSH 插件

将以下正文交给拥有 SaaS 仓库访问权限的研发 Agent。仓库、测试环境、公开测试技能及部署权限由 SaaS 项目提供；示例值不是实际配置。正式契约以本文引用的 OpenSpec 和插件实际消费代码为准。

---

你负责在 SkillHub SaaS 中核验并补齐原生 DSH 插件所需的 Registry HTTP API，使同一个插件包能够连接开源版或 SaaS 实例，完成匿名浏览、搜索、查看精确版本详情、下载以及在 DSH 主机上的安装管理。

请先调查 SaaS 当前实现，再进行最小必要改造。不要假定开源与 SaaS 的类名、数据库、网关或对象存储相同，也不要仅复制开源 Controller 而遗漏访问范围和版本规则。

## 1. 先阅读的资料

从开源 SkillHub 仓库读取：

- `openspec/changes/add-dsh-native-plugin/registry-api-contract.md`：HTTP 契约、同步边界和验收。
- `openspec/changes/add-dsh-native-plugin/interface-design.md`：字段、接口和错误语义。
- `plugins/dsh/src/host/registry-adapter.ts`：插件实际消费的路径、校验和降级条件。
- `plugins/dsh/scripts/probe-registry.ts` 与 `plugins/dsh/README.md`：现有匿名契约探针及命令。
- `openspec/changes/add-dsh-native-plugin/conversation-design.md`：对话搜索及本地安装状态边界。

记录实际使用的开源提交和 SaaS 提交，在交接报告中给出差异；不要把旧文档或探针通过视为当前 SaaS 已兼容。

## 2. 工作范围与禁止扩大项

SaaS 提供公开技能数据、固定版本内容和下载包。DSH 插件 Host 负责本机文件安装、升级、卸载、安装清单和对话工具；浏览器 Client 通过插件 Remote 调用 Host。

本任务不增加登录流程、Token 配置、付费能力、权限产品、评论写入、插件市场、远程操作用户文件系统或服务端安装任务。保持已有租户、可见性、下架、审核和下载策略，不因接入插件而扩大公开范围。

若 SaaS 只能登录后读取必需接口，明确报告与匿名 MVP 的冲突，并提供需产品确认的选项；不得暗中绕过认证、复用管理凭证或设计隐式登录。

## 3. 必须逐项核对的接口

JSON 成功响应使用插件当前支持的 `ApiResponse.data` 包装。以下字段是消费要点，完整结构应逐项对照适配器 schema，不自行发明替代字段。

| 能力 | 路径 | 重点约束 |
| --- | --- | --- |
| 广场目录 | `GET /api/web/skills` | `q/page/size/sort`，页码从 0 开始，插件每批请求 12 项，`sort=newest/downloads/rating`；`data.items/total/page/size` 真实且一致，候选版本来自 `publishedVersion.version` |
| 对话搜索 | `GET /api/cli/v1/skills/search` | `q` 可空；返回 `items/total/limit`，条目含 `namespace/slug/latestVersion/summary`；不假设支持分页，不伪造 total |
| 精确版本解析 | `GET /api/cli/v1/skills/{namespace}/{slug}/resolve` | 核对 `version` 查询语义；成功提供坐标、`version/versionId/fingerprint/downloadUrl`；指定精确版本时不得悄悄返回 latest |
| 技能元信息 | `GET /api/v1/skills/{namespace}/{slug}` | `id`、坐标、`displayName/summary` 与同一技能一致，不能串租户或暴露管理字段 |
| 技能说明 | `GET /api/v1/skills/{namespace}/{slug}/versions/{version}/file?path=SKILL.md` | 返回固定版本原始文件内容；不能包装为 JSON，允许现有 octet-stream 类型 |
| 技能归档 | 使用 `resolve.downloadUrl` 返回地址 | 返回完整 ZIP 或受支持跳转；包与解析的版本和 fingerprint 一致，不将 URL 写死为另一个接口 |

广场的 `displayName/downloadCount/starCount/ratingAvg/ratingCount` 按真实可选值提供；不伪造评分、下载量或头像，不因老数据缺少可选字段而使全部目录不可读。

目录兼容回退由现有插件处理：仅首批无标签目录请求返回 404/405 时回退受限 CLI 搜索。401/403、429、5xx、超时和畸形数据不能冒充“旧版本不支持”；后续分页失败不能静默换成第一页。

## 4. 可选能力

| 能力 | 路径或约束 | 未具备时的处理 |
| --- | --- | --- |
| 标签筛选 | `GET /api/web/labels`；目录请求附 `label` 参数 | 标签项含 `slug/type/displayName`，type 沿用 `RECOMMENDED/PRIVILEGED`；匿名只公开允许展示的标签。未上线可无此路由，不能显示标签但忽略筛选 |
| 版本历史 | `GET /api/v1/skills/{namespace}/{slug}/versions` | 分页、版本与 nullable 字段保持契约；插件支持 auto/show/hide |
| 评论 | `GET /api/v1/skills/{skillId}/reviews` | 技能级只读；未上线可配置 hide，不为接入强制研发评论系统 |
| 安全审核 | `GET /api/v1/skills/{skillId}/versions/{versionId}/security-audit` | 版本级；保留匿名拒绝，不能为使 Tab 可见开放内部报告 |

配置隐藏由插件现有 detailTabs 承担，不新增未经定义的 capability 接口。资源 404 与整个模块不存在、空集合与暂时故障须分别处理。

## 5. 本地“已安装”识别：SaaS 不需新增接口

此能力由 DSH 主机读取本地安装记录并检查必要文件完成，按 Registry、namespace、slug 匹配；用户级和当前项目级的版本、异常及查询时间由插件提供。

- SaaS 不接收或保存用户机器的安装路径、本地技能清单、项目目录、安装 token 或文件 fingerprint 清单。
- 市场 API 不返回针对某台 DSH 的 `installed` 布尔值，也不以账号云端历史冒充本机已安装。
- SaaS 需要保持 Registry 基地址及技能坐标语义稳定。域名或部署基础路径迁移须说明对现有本地来源匹配的影响；不要自行把不同域名认作同一来源。
- 没有当前来源记录，不等于 DSH 没有手工安装或其他来源技能。服务端无需为枚举其他来源增设能力。
- fingerprint 继续用于原有固定版本内容核验；它不等于安全审核结论。

## 6. 安全与部署适配检查

1. 核对公开市场、搜索、resolve、版本、文件和下载使用一致的可见性及租户过滤，不能通过已知坐标绕过私有或下架限制。
2. 核对命名空间与坐标编码、反向代理基础路径、HTTPS、对象存储跳转、签名 URL 有效期和匿名限流。不要通过放宽 SSRF、防路径穿越或跨租户规则来满足插件。
3. 插件 Host 发起 HTTP 请求；不要默认开放浏览器 CORS，也不要让用户提交任意服务器本地路径。
4. 保持 401/403/404/429/5xx 的实际语义；日志、报告和示例不得包含凭证、带签名下载地址或内部敏感信息。
5. 新增 API 字段保持向后兼容；变更路径、类型、访问规则、版本或指纹算法时，先核对开源契约并说明迁移，不单方面修改插件协议。

## 7. 实施步骤

1. 阅读 SaaS 仓库适用说明和测试入口，定位真实路由、DTO、公开访问规则、版本解析、文件下载、存储和网关配置。
2. 先提交一份差异表：能力、当前证据、缺口、拟改模块、是否影响旧客户端、验证方式。已有能力保持复用，确有缺项才改造；重大访问策略冲突先确认。
3. 实现必要改动和行为测试，更新 SaaS 的 OpenAPI 与对应文档；如需迁移，独立说明必要性、回退和部署顺序。
4. 先完成测试环境的匿名契约验证，再完成真实 DSH 安装验收。不将 mock、单测或静态对照写成部署验收。
5. 创建供审阅的 PR，说明可用范围和未通过项；部署、合并和发布按 SaaS 项目授权执行，不默认操作生产。

## 8. 验证要求

### 服务端测试

至少覆盖正常读取、空查询、零结果、多页及排序、标签实际过滤、缺少可选字段、nullable 字段、同名不同命名空间、精确旧版本、版本不存在/撤销、私有或跨租户拒绝、原始文件流、下载跳转、完整归档与指纹一致性。实际限流、存储及网关需在环境层补验，不能以 Controller mock 替代。

### 匿名契约探针

在开源仓库 `plugins/dsh` 目录，使用公开测试技能及精确版本运行现有命令：

```sh
bun run probe:registry --registry 'https://registry.example' --namespace 'demo' --slug 'test-skill' --version '1.0.0' --output /absolute/path/to/registry-report.json
```

地址、坐标和输出路径替换为实际测试值，使用新的输出文件。该命令不是 SaaS 部署命令，也不安装或执行技能；只解包到临时目录核验。探针不覆盖完整广场分页、排序、标签和真实 DSH 页面，须追加验收。

### DSH 测试环境验收

使用独立测试目录和测试技能，记录实际 DSH、插件包及依赖组合；按插件发布兼容表执行，不能仅用一个主包版本推断全部依赖兼容。

- 配置 SaaS Registry，测试连接、保存后浏览搜索；分页、排序、标签生效。
- 读取固定版本详情及资源说明；隐藏未上线评论，匿名审核拒绝正常呈现。
- 用户级、项目级安装带资源技能，核对实际文件与版本，再进行受保护的升级和卸载。
- 若使用包含安装状态识别的新插件候选包，验证卡片与工具结果的版本/范围、异常/未知状态及来源切换隔离；此功能不要求修改 SaaS API。
- 对话搜索、唯一候选选择和安装；模型测试单独确认可用配置与调用预算，不使用模拟回复冒充真实模型。
- 观察 DSH 是否真正发现并读取技能资源；“文件安装完成”和“模型实际采用技能”分别记录。

## 9. 交付内容与完成条件

交付 SaaS PR、接口差异表、变更文件说明、测试命令和结果、OpenAPI 更新、测试环境部署版本、脱敏探针报告、真实 DSH 验收记录、可选能力配置和未完成项。大文件、截图、日志按 SaaS 仓库规范存放，不将私人数据或临时预览产物堆进 PR。

必需接口在目标测试环境通过匿名契约和 DSH 安装验收后，才能声明对应 SaaS 部署可用。未验证的生产环境、其他租户、DSH 版本、平台及真实模型效果单独列出，不扩大结论。
