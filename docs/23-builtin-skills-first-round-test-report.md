# 内置 Skill 候选第一轮质量与安全实测报告

> 审计日期：2026-07-30
> 审计对象：[内置 Skill 候选池](./22-builtin-skills-candidate-pool.md)中的 70 个候选
> 目标：判断上游版本能否作为 SkillHub 内置 Skill 原样分发，以及哪些候选值得适配后进入下一轮

## 1. 结论

不建议把这 70 个候选原样批量内置。

| 结论 | 数量 | 含义 |
|---|---:|---|
| `include` | 3 | 功能与安全边界基本成立；补齐来源、许可证和版本元数据后可进入打包复核 |
| `adapt` | 29 | 场景有价值，但必须先修正文案、权限、文件边界、隐私或依赖问题，再回归测试 |
| `hold` | 16 | 依赖账号、CLI、付费 API、私有数据或特定平台，本轮无法证明可开箱即用 |
| `reject` | 22 | 存在明确安全缺陷、质量失败、再分发限制，或与 SkillHub 场景明显不兼容 |

最终人工风险分布为：低风险 13 个、中风险 23 个、高风险 26 个、极高风险 8 个。没有发现可以认定为恶意软件的证据，但发现了多项足以阻止内置的真实缺陷。

当前版本只有 A05、A13、B10 三项接近“补元数据即可打包”。若完成 29 项适配，理论上可保留 32 项；考虑改造成本，建议先收敛出 15–20 个低风险首批包，再扩到 30 个，不要为了数量降低准入线。

## 2. 测试范围与方法

### 2.1 固定的上游快照

| 来源 | 审计提交 |
|---|---|
| `anthropics/skills` | `b29e7cf65e5cb78a5ac33d582270551bc74a14eb` |
| `github/awesome-copilot` | `be7a1cf734f427d50266335b461b86977299d953` |
| `GarethManning/education-agent-skills` | `32fce5c0d097ec675cf81c750a65a379e4d87e3c` |
| `cosmicstack-labs/mercury-agent-skills` | `4c57cf2eaeb3fb9c0e418615c7a36fe977c88b79` |
| `openclaw/openclaw` | `62cbbcc800214f05cdc4b97debdf7339bfa7c5f4` |
| `NKZ55/research-planner` | `ee4e8753de02cb83df713934597b45f6778f7254` |
| `ailabs-393/ai-labs-claude-skills` | `1a12bc7aadcc7b211f77a7455db454b77a71f827` |
| `openai/plugins` | `11c74d6ba24d3a6d48f54a194cd00ef3beea18f9` |
| `vercel-labs/agent-skills` | `7c180d9044c9ae2b442b567aad4e42a28dd5ed62` |

本轮共检查 630 个文件、约 10.96 MB，其中包括 84 个代码文件和 9 个二进制文件。

### 2.2 执行的检查

1. **逐文件人工审查**：检查脚本、引用文件、模板、二进制、网络访问、凭据、隐私数据、外部写入、文件覆盖、动态安装和宿主绑定。
2. **SkillHub 实际包校验**：使用项目中的 `SkillPackageValidator` 校验 70 个目录；70/70 结构通过，但 3 个包共产生 57 条扩展名警告。
3. **静态安全扫描**：使用 Cisco AI Skill Scanner 1.0.2 的静态分析器、官方 balanced YARA 规则，以及 SkillHub 追加的正则规则。
4. **语法与格式检查**：覆盖 Python AST、Shell `bash -n`、JSON/YAML、XML/XSD/SVG、Office/ZIP 归档及字体解析。
5. **任务级测试**：对无外部副作用的 Skill 做提示词任务回放、纯函数测试或本地文件测试；天气查询和视频抽帧完成了真实端到端执行。
6. **阻断测试**：涉及真实账号、私有笔记、浏览器 Cookie、摄像头、付费 API、上传、部署、推送、删除或外部发消息的路径不执行，只做静态审查。

“实测”不代表已在所有 Agent 宿主和操作系统完成兼容性认证；每项实际覆盖范围见下表。

### 2.3 工具限制

- 本地 Docker 扫描服务未运行，因此没有执行 LLM 行为分析和 VirusTotal 查询。
- 项目追加的 `scanner/examples/vetter-rules/yara/skillhub_vetter.yara` 无法编译，错误为第 146 行存在未引用的 `$local_storage`。本轮保留了项目追加正则，但 YARA 使用 Cisco 官方规则。
- 自动扫描共给出 98 条发现，其中 53 条只是 frontmatter 缺少 `license`。原始最高严重度分布为：`CRITICAL 4 / HIGH 7 / MEDIUM 8 / INFO 43 / SAFE 8`。
- 自动严重度不能作为最终结论：A13 的 `CRITICAL` 来自“忽略外部内容中的指令”这一防护语句；相反，B04、C07、C09、C19、D07、D10 等真实高风险行为被漏报或低估。

## 3. 关键安全与质量发现

1. **目录越界和本地数据泄露**
   - B04 会跟随文件或目录符号链接，把 Skill 目录外内容打入包。
   - B13 允许恶意 Markdown 用绝对路径或 `../../` 读取本地图片并嵌入 DOCX。

2. **无确认破坏用户文件**
   - B14、B15 发现输出目录已存在时会直接 `shutil.rmtree` 整棵删除。
   - B16、A14 等会覆盖现有输出，缺少 no-clobber 或确认机制。

3. **执行、上传和凭据边界过宽**
   - C07 可启动任意 stdio 命令、连接任意 URL，并让模型自动调用工具。
   - C09 会把大部分项目源码上传到非官方中转服务。
   - C19 明确导入 Chrome Cookie；D07 会显示和搜索部署 token；D10 涉及摄像头凭据、局域网发现和任意 action。
   - C20 允许通过 `OPENAI_BASE_URL` 把 API key 与音频发送到任意端点。

4. **远程提示与不可信内容**
   - C11 每次运行都下载未固定提交和哈希的远程 Markdown，并把它作为指令执行。
   - B17 会把输入中的 `<script>` 原样带入 HTML。
   - 多个网页、会议稿、CI 日志和研究类 Skill 没有把外部内容明确隔离为“不执行的输入数据”。

5. **再分发和包兼容性**
   - D03–D06 的许可证明确禁止复制和再分发，不能作为 SkillHub 内置包发布。
   - B03 的 54 个 `.ttf`、C09 的 `Archive.zip`、D08 的两个 `.lobster` 文件会触发 SkillHub 扩展名警告；当前校验器只警告、不阻断。
   - 53 个 Skill 的 frontmatter 未声明 `license`。即使来源仓库有许可证，正式包仍应附带许可证、上游提交、作者、修改说明和归属信息。

## 4. 逐项测试结果

质量评分：5 为完整、准确且可复用；3 为可用但需明显修正；1 为核心功能或安全边界不成立。

### A：优先实测

| ID | Skill | Q | 风险 | 测试 | 结论 | 主要证据 |
|---|---|---:|---|---|---|---|
| A01 | `internal-comms` | 3 | 高 | 任务回放通过 | adapt | 3P 周报输出正确；其他模板会广泛读取 Slack、邮箱、Drive、日历，需限制数据范围并在发布前确认。 |
| A02 | `meeting-minutes` | 3 | 中 | 任务回放通过 | adapt | 决策、负责人和日期提取正确；固定 12 节与“一页内”冲突，创建任务或发布必须显式授权。 |
| A03 | `brag-sheet` | 4 | 中 | 任务回放通过 | adapt | 能保持指标与证据；Backfill 会扫描 Copilot session、Git 和登录态 `gh`，需改成用户指定来源。 |
| A04 | `convert-plaintext-to-md` | 2 | 中 | 任务回放部分通过 | reject | 能整理文本，但规则过重、文件操作语义不一致，并绑定 Copilot `#tool:fetch`，重写成本高于保留价值。 |
| A05 | `exam-ready` | 4 | 低 | 任务回放通过 | include | 输出未越出课程材料，输入和产出边界清楚；正式包补许可证和外部内容防注入说明。 |
| A06 | `spaced-practice-scheduler` | 3 | 低 | 任务回放通过 | adapt | 4 周 12 课安排可执行；“24 小时遗忘 70%”及 40% 重教阈值表述过度。 |
| A07 | `ai-claim-checker` | 4 | 低 | 任务回放通过 | adapt | 能识别“季节由近日点造成”的错误；需修正来源示例并弱化强制流程。 |
| A08 | `decision-matrix` | 3 | 中 | 计算实测通过 | adapt | 实测计算可复现，但内置示例总分算错；高分不应直接决定高风险选择。 |
| A09 | `storytelling-advisor` | 3 | 低 | 任务回放通过 | adapt | 故事结构有效；内置示例会从稀疏输入虚构姓名、规模、周期和用户数。 |
| A10 | `linkedin-post-formatter` | 3 | 低 | 任务回放通过 | adapt | 字符映射正确且不会自动发布；Unicode 样式影响无障碍，平台算法规则容易过期。 |
| A11 | `documentation-writer` | 3 | 低 | 任务回放部分通过 | adapt | Diátaxis 分类清楚；即使输入充分仍强制提问和等待大纲批准，影响一次性交付。 |
| A12 | `diagram-maker` | 3 | 中 | HTML/SVG 实测通过 | adapt | 独立 SVG 的结构、箭头和远程资源检查通过；默认写 `diagram.html` 需防覆盖。 |
| A13 | `weather` | 4 | 低 | 真实网络查询通过 | include | `wttr.in` JSON 可解析三日预报，已有外部内容防注入和严重天气改查官方来源的边界。 |
| A14 | `video-frames` | 3 | 中 | 本地端到端通过 | adapt | 从合成视频成功抽出 PNG，错误输入能失败；`--index` 缺显式数值校验，且 `ffmpeg -y` 会覆盖输出。 |
| A15 | `frontend-design` | 4 | 中 | 任务回放通过 | adapt | 设计约束与可访问性要求有效；必须删除“读取 human memory”并只用本次授权上下文。 |

### B：扩展实测

| ID | Skill | Q | 风险 | 测试 | 结论 | 主要证据 |
|---|---|---:|---|---|---|---|
| B01 | `theme-factory` | 3 | 低 | 渲染部分通过 | adapt | 主题展示可渲染，PDF 无脚本或表单；两个主题对比度不足，缺 WCAG 校验和应用机制。 |
| B02 | `algorithmic-art` | 3 | 中 | 静态 | adapt | 依赖无 SRI/CSP 的外部 p5.js 和字体，模板仍有待填骨架，且缺文档声称的下载按钮。 |
| B03 | `canvas-design` | 3 | 高 | 54 字体解析通过 | adapt | 字体文件有效；允许任意下载字体、许可不完整，且 54 个 TTF 触发包警告。 |
| B04 | `skill-creator` | 4 | 极高 | 静态安全失败 | reject | 存在 symlink 越界打包、本地报告 XSS、无确认终止端口进程和默认高并发外发模型数据。 |
| B05 | `webapp-testing` | 3 | 高 | 帮助通过，依赖阻断 | hold | 缺 Playwright；runner 使用 `shell=True`、进程组处理不完整，还可能误认同端口的既有服务。 |
| B06 | `daily-standup-journal` | 4 | 中 | 任务回放通过 | adapt | 日常站会产出清楚；滚动日志和跨日情绪推断缺同意、保存位置及删除机制。 |
| B07 | `time-blocking-scheduler` | 3 | 低 | 任务回放通过 | adapt | 能安排重点任务与缓冲；“深度工作永远在中午前”等绝对规则不适合所有用户。 |
| B08 | `resume-writing` | 2 | 中 | 任务回放不充分 | hold | 仅给出泛化 STAR/CAR 建议，缺事实保真、岗位证据映射和作品集流程。 |
| B09 | `interview-prep` | 2 | 低 | 任务回放不充分 | hold | 只能生成泛化清单，缺计划、模拟评分和岗位校准；正文没有 description 声称的谈薪内容。 |
| B10 | `retrieval-practice-generator` | 5 | 低 | 任务回放通过 | include | 题型、难度、纠错、局限和验证要求完整；按 CC BY-SA 补齐归属、同许可及修改声明。 |
| B11 | `study-strategy-selector` | 4 | 低 | 任务回放通过 | adapt | 能组合检索、间隔和交错练习；部分学习科学结论被写成绝对规律。 |
| B12 | `research-planner` | 4 | 中 | 任务回放通过 | adapt | 32 个模板覆盖目标、招募、同意和时间线；需移除安装动作并加强研究隐私与法律复核。 |
| B13 | `md-to-docx` | 2 | 高 | 语法通过，依赖阻断 | hold | 无 lockfile 且依赖未安装；图片路径未限制在输入目录，恶意 Markdown 可读取本地文件并打包。 |
| B14 | `convert-word-to-md` | 3 | 极高 | AST/help 通过，依赖阻断 | reject | 已存在输出目录会被无确认递归删除。 |
| B15 | `convert-pdf-to-md` | 3 | 极高 | AST/help 通过，依赖阻断 | reject | 与 B14 相同会删除整个输出目录，依赖范围也未锁定。 |
| B16 | `convert-excel-to-md` | 3 | 高 | AST/help 通过，依赖阻断 | hold | 会删除现有 `img/` 并覆盖 Markdown，缺少确认和 no-clobber。 |
| B17 | `markdown-to-html` | 2 | 高 | Pandoc 本地实测失败 | reject | 输入 `<script>` 被原样保留；单个 Skill 混合多套工具、全局安装、`@latest`、插件执行和公网监听。 |
| B18 | `ad-campaign-analyzer` | 3 | 中 | 任务回放部分通过 | adapt | 能定位 CPA 异常；固定样本门槛和线性预算推断可能误导花费决策。 |
| B19 | `gtm-positioning-strategy` | 3 | 中 | 任务回放部分通过 | adapt | 结构可用；效果门槛缺统计依据，还要求直接冷邮件触达，必须改成只起草并确认发送。 |
| B20 | `competitor-ad-intelligence` | 3 | 高 | 联网路径阻断 | reject | 任意 URL 抓取带来 SSRF、恶意网页和提示注入面，核心业务推断缺可靠依据。 |
| B21 | `blogwatcher` | 2 | 高 | CLI 缺失 | hold | 只是第三方 CLI 速查，安装使用未固定的 Go `@latest`，不可复现。 |
| B22 | `openai-whisper` | 3 | 中 | CLI/模型缺失 | hold | 本地转写有价值，但首次下载大型模型；缺音频隐私、资源上限、版本和缓存删除策略。 |
| B23 | `songsee` | 2 | 高 | CLI 缺失 | hold | 依赖第三方 Homebrew tap 和隐式 ffmpeg，当前只有命令速查，无法证明开箱可用。 |
| B24 | `meeting-note-summarizer` | 3 | 中 | 任务回放事实性失败 | adapt | 格式清晰；示例会把试探日期固化，并凭空增加时长、任务、负责人和截止日。 |
| B25 | `travel-planner` | 2 | 高 | 本地核心测试失败 | reject | 不同目的地生成相同占位行程；代码仍有 TODO，并把饮食、无障碍和历史行程明文存入 `~/.claude`。 |

### C：条件候选

| ID | Skill | Q | 风险 | 测试 | 结论 | 主要证据 |
|---|---|---:|---|---|---|---|
| C01 | `notion-knowledge-capture` | 4 | 中 | 账号/写入阻断 | hold | 模板完整；会搜索、创建和更新私有 Notion 页面，缺最小范围和最终写入确认。 |
| C02 | `notion-meeting-intelligence` | 4 | 中 | 账号/写入阻断 | hold | 会聚合内部会议、OKR、规格和参会者上下文，并创建或更新页面。 |
| C03 | `notion-research-documentation` | 4 | 中 | 账号/写入阻断 | hold | 研究和引用流程完整；跨私有页面汇总并发布前需限定读取范围和确认。 |
| C04 | `notion-spec-to-implementation` | 4 | 中 | 账号/写入阻断 | hold | 规格拆解成熟；会批量创建计划、任务和状态，缺数量预览和最终确认。 |
| C05 | `gh-address-comments` | 4 | 中 | 纯函数实测通过 | adapt | 两页分页模拟通过，“无限循环”为误报；需增加页数/游标保护并限制私有评论输出。 |
| C06 | `gh-fix-ci` | 4 | 高 | 解析实测通过 | adapt | Run/job URL 和失败片段解析正确；输出 CI 日志前缺 token、密码等敏感信息脱敏。 |
| C07 | `mcp-builder` | 3 | 极高 | 外部执行阻断 | reject | 可启动任意命令、连接任意 URL、自动调用 MCP 工具并把结果发送给 Anthropic；扫描器误判 SAFE。 |
| C08 | `slack-gif-creator` | 3 | 中 | 纯函数通过，依赖阻断 | adapt | `Image.open` 外传告警为误报；需限制输出覆盖、图片解压炸弹并锁定依赖。 |
| C09 | `deploy-to-vercel` | 2 | 极高 | 语法通过，上传阻断 | reject | 会把大部分项目上传到非官方中转端点，排除规则不足以保护源码和密钥；ZIP 还触发包警告。 |
| C10 | `vercel-react-best-practices` | 5 | 低 | 静态通过 | adapt | 70 条规则完整；需删除记录原始 session cookie、缓存认证 cookie 等危险示例，并补 CSP 说明。 |
| C11 | `writing-guidelines` | 2 | 高 | 远程指令阻断 | reject | 每次下载未固定 commit/hash 的远程 Markdown 并当成指令，存在供应链与远程提示注入风险。 |
| C12 | `summarize` | 4 | 高 | CLI/外部上传阻断 | adapt | 会把 URL、本地文件或媒体发往模型、Firecrawl、Apify；应默认关闭第三方 fallback 并披露目的地。 |
| C13 | `goplaces` | 3 | 高 | API/计费阻断 | hold | 需要计费 Google API，可能发送精确经纬度；缺费用和位置隐私确认。 |
| C14 | `obsidian` | 4 | 高 | 私有数据/写入阻断 | hold | 可读取私有 vault 和配置，并支持删除、插件重载及 `eval`；服务器部署通常也不可用。 |
| C15 | `notion` | 4 | 高 | 账号/写入阻断 | reject | 支持创建、更新、trash、上传和 raw curl，安全边界弱于 C01–C04 且功能重复。 |
| C16 | `apple-notes` | 3 | 高 | 私有数据/写入阻断 | reject | 第三方 CLI 可编辑、删除、移动和导出私人笔记，macOS 专属且缺确认。 |
| C17 | `apple-reminders` | 4 | 高 | 私有数据/写入阻断 | hold | 日常价值较高，但能读取私人计划并强制删除；应拆成默认只读版并逐项确认。 |
| C18 | `trello` | 2 | 高 | 凭据/写入阻断 | reject | 完整账号 token 放在 URL 查询串中，并直接创建、移动、评论和归档，无确认。 |
| C19 | `spotify-player` | 3 | 极高 | Cookie 访问阻断 | reject | 明确要求从 Chrome 导入 Spotify Cookie，触及浏览器凭据边界。 |
| C20 | `openai-whisper-api` | 3 | 高 | Shell/help 通过，上传阻断 | adapt | 任意 `OPENAI_BASE_URL` 可接收 key 与音频；还缺 `curl --fail`、25 MB 预检和防覆盖。 |

### D：原计划不纳入

| ID | Skill | Q | 风险 | 测试 | 结论 | 主要证据 |
|---|---|---:|---|---|---|---|
| D01 | `yeet` | 4 | 高 | Git/外部写入阻断 | adapt | Git 范围防护较好；但会安装依赖并 stage、commit、push、建 PR，安装和 push 前必须确认。 |
| D02 | `chatgpt-app-submission` | 4 | 中 | 静态 | hold | 审核清单成熟且只写本地文件；场景窄、绑定 OpenAI/MCP，不适合作为首批通用内置。 |
| D03 | `docx` | 4 | 高 | 语法/静态通过 | reject | 许可证禁止再分发；还会动态编译并 `LD_PRELOAD` socket shim，LibreOffice 超时可能误报成功。 |
| D04 | `pdf` | 4 | 中 | 纯函数部分通过 | reject | 自动“外传”告警为本地读取误报，但许可证禁止再分发，且不可信 PDF 有解析与资源风险。 |
| D05 | `pptx` | 4 | 高 | 语法/静态通过 | reject | 提示注入告警为词法误报；真实工具链会修改文档并使用 LibreOffice/LD_PRELOAD，且禁止再分发。 |
| D06 | `xlsx` | 4 | 高 | 语法/静态通过 | reject | 扫描器误判 SAFE；会原地重写工作簿，`--force` 可破坏外部链接，且禁止再分发。 |
| D07 | `vercel-cli-with-tokens` | 3 | 极高 | 凭据/部署阻断 | reject | 指示显示 token、搜索 `.env`，并包含全局安装、部署、域名变更及真实付费操作。 |
| D08 | `taskflow` | 3 | 高 | YAML/示例解析通过 | reject | 示例允许任意命令、PR 操作和 Slack/Telegram 外发，完全绑定 OpenClaw；`.lobster` 触发包警告。 |
| D09 | `taskflow-inbox-triage` | 3 | 高 | 静态 | reject | 分类私人收件箱、持久化状态并向 Slack 路由内容，依赖 D08 且缺隐私最小化和外发确认。 |
| D10 | `camsnap` | 2 | 极高 | 摄像头路径阻断 | reject | 摄像头账号密码出现在命令行，支持局域网发现、抓图、录像和任意 `--action`；扫描器完全漏报。 |

## 5. 建议的下一步

第一批最终选定以下 15 项：

- 近似可直接打包：A05、A13、B10。
- 低成本修正后复测：A07、A08、A09、A10、A11、A12、A14、A15、B06、B07、B11、B24。

这批覆盖 4 个学习与研究、5 个办公与个人效率、4 个内容与设计、2 个日常出行与媒体 Skill。
完成统一许可证元数据、外部内容隔离、no-clobber 和宿主去绑定后，再进行第二轮打包与回归测试。

未选择 A06，是因为它与 B10、B11 的学习流程重叠；未选择 B01，是因为主题对比度和实际应用机制
尚未成立；未选择 C10，是因为首批已有 A15，且 C10 仍有 cookie 示例和许可证问题。B25 的旅行
规划实测失败，不因旅行场景暂时缺少候选而降低准入标准。

其余 `adapt` 项不要与首批并行铺开：先逐项明确替代哪个首批候选、所需依赖、数据边界和验收用例。所有 `hold` 项应在隔离环境和测试账号中完成端到端验证；所有 `reject` 项默认不进入内置目录。

## 6. 第一批 15 个适配包复测

上述第一轮报告检查的是固定上游快照。完成首批适配后，又对仓库
`builtin-skills/skills/` 中的最终版本做了一轮发布前复测：

| 检查 | 结果 |
|---|---|
| 目录、元数据、来源和许可证 | 15/15 通过；每包包含 `SKILL.md`、`LICENSE.txt` 和 `NOTICE.md` |
| SkillHub 生产包校验器 | 15/15 通过，0 error、0 warning |
| 确定性打包 | 连续两次构建的 15 个 ZIP 逐字节一致；符号链接输入被拒绝 |
| 静态安全扫描 | Cisco AI Skill Scanner 1.0.2 官方 balanced YARA + SkillHub 追加正则：15/15 `SAFE`，0 finding |
| `video-frames` 真实执行 | FFmpeg 合成视频抽帧成功；非法索引、冲突参数和覆盖已有输出均被拒绝 |
| 人工内容复核 | 已检查事实保真、外部内容隔离、隐私、隐藏记忆、文件覆盖和未经授权的外部写操作 |

适配后重点删除或收紧了固定日程模板、套娃提示词、暗中虚构、跨会话记忆、过度确定的学习效果
表述和宿主专属路径。`weather` 仍需要联网，`video-frames` 仍需要本机 FFmpeg；其余首批 Skill
没有运行时账号或密钥依赖。

本轮结论不等于所有 Agent 宿主和所有模型都已认证。未运行 LLM 行为扫描和 VirusTotal；发布
前仍需在最终支持的宿主中用 `builtin-skills/evals.json` 的 15 个任务做一次行为回归，并在干净
SkillHub 实例验证远程同步、下载和安装。
