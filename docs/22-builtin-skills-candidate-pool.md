# SkillHub 内置 Skills 候选池

> 目标：从公开来源中筛出 30～50 个可合法再分发、真实可用的 Skill，随 SkillHub 默认部署提供。
>
> 初筛日期：2026-07-30
>
> 建设原则与验收标准见：[SkillHub 内置优质 Skills 建设计划](./21-official-starter-skills-plan.md)
>
> 首轮审计结果见：[内置 Skill 候选第一轮质量与安全实测报告](./23-builtin-skills-first-round-test-report.md)

## 1. 初筛结论

本轮共整理 70 个候选，分成四组：

| 分组 | 数量 | 含义 |
|---|---:|---|
| A：优先实测 | 15 | 来源和许可证相对清楚、依赖较少，先做包级审查和真实任务测试 |
| B：扩展实测 | 25 | 场景有价值，但需要适配、安装工具或进一步核验 |
| C：条件候选 | 20 | 依赖账号、密钥、特定平台，或许可证仍需补证 |
| D：不纳入 | 10 | 许可证不允许、外部写操作风险高，或过于平台专属 |

A、B 两组只是测试前的实测队列，不等于已经批准内置。首轮审计最终得到 `include 3 / adapt 29 /
hold 16 / reject 22`；第一批从 `include` 和低成本 `adapt` 项中选出 15 个。

### 第一批最终选定：15 个

第一轮测试后，不再沿用原 A 组作为第一批清单。最终选定 3 个接近可直接打包的候选和 12 个
低成本适配候选：

| 编号 | Skill | 主要场景 | 首轮结论 | 适配要求（已完成） |
|---|---|---|---|---|
| A05 | `exam-ready` | 学习与备考 | include | 补许可证元数据和外部内容防注入说明 |
| A07 | `ai-claim-checker` | 信息核查 | adapt | 修正来源示例、弱化强制流程，补 CC BY-SA 归属 |
| A08 | `decision-matrix` | 工作与日常决策 | adapt | 修正示例算分，增加高风险决策免责声明 |
| A09 | `storytelling-advisor` | 写作与表达 | adapt | 明确区分用户事实和创作补充，禁止暗中虚构 |
| A10 | `linkedin-post-formatter` | 社交内容 | adapt | 普通文本作为默认输出，样式改为显式选择 |
| A11 | `documentation-writer` | 办公文档 | adapt | 输入充分时直接起草，不强制重复提问和审批大纲 |
| A12 | `diagram-maker` | 流程图与结构图 | adapt | 增加输出防覆盖，去除 OpenClaw 路径约定 |
| A13 | `weather` | 日常与出行天气 | include | 补来源和许可证元数据 |
| A14 | `video-frames` | 视频抽帧 | adapt | 增加 `--index` 校验和输出防覆盖 |
| A15 | `frontend-design` | 界面与创意设计 | adapt | 删除隐式读取 human memory 的指令 |
| B06 | `daily-standup-journal` | 日报与个人复盘 | adapt | 默认不跨日保存或推断，持久化必须由用户选择 |
| B07 | `time-blocking-scheduler` | 个人时间管理 | adapt | 删除固定节律规则，优先使用用户作息和约束 |
| B10 | `retrieval-practice-generator` | 主动回忆练习 | include | 补 CC BY-SA 归属、同许可和修改声明 |
| B11 | `study-strategy-selector` | 学习策略 | adapt | 修正过度绝对的学习科学表述，补 CC BY-SA 归属 |
| B24 | `meeting-note-summarizer` | 会议摘要与行动项 | adapt | 禁止补造负责人、日期、时长和任务，未知项明确标注 |

这 15 项的适配后源码和归属信息现已纳入 `builtin-skills/skills/`，最终复测结果见
[测试报告第 6 节](./23-builtin-skills-first-round-test-report.md#6-第一批-15-个适配包复测)。

组合分布：

- 学习与研究：A05、A07、B10、B11，共 4 个。
- 办公与个人效率：A08、A11、B06、B07、B24，共 5 个。
- 内容与设计：A09、A10、A12、A15，共 4 个。
- 日常出行与媒体：A13、A14，共 2 个。

没有选择 A06，是因为它与 B10、B11 的学习流程重叠；没有选择 B01，是因为主题对比度和实际应用
机制尚未成立；没有选择 C10，是因为首批已有 A15，且 C10 仍有 cookie 示例和许可证问题。
旅行规划候选 B25 已在实测中失败，不因场景缺口重新纳入。

当前 manifest 中的两个包也不计入上述数量：

- `skillhub-hello` 是教学演示内容，可以保留作部署验证，但不计入 30～50 个实用 Skill。
- `agentguard` 包含 Node.js 依赖和较广的文件、命令访问范围，完成独立安全与兼容性复核后再决定
  是否计入。

## 2. 去哪里找

优先从这些可追溯的上游仓库找，不直接从聚合站按热度批量搬运：

| 来源 | 适合场景 | 许可证结论 | 使用方式 |
|---|---|---|---|
| [GitHub Awesome Copilot](https://github.com/github/awesome-copilot/tree/main/skills) | 办公、写作、学习、业务 | 仓库为 [MIT](https://github.com/github/awesome-copilot/blob/main/LICENSE) | 主要候选源 |
| [OpenClaw Skills](https://github.com/openclaw/openclaw/tree/main/skills) | 工具、媒体、信息获取、个人应用 | 仓库为 [MIT](https://github.com/openclaw/openclaw/blob/main/LICENSE) | 逐项检查 CLI、账号和操作系统依赖 |
| [Anthropic Skills](https://github.com/anthropics/skills/tree/main/skills) | 办公、设计、开发 | 逐 Skill 授权；部分 Apache-2.0，文档处理四项不可再分发 | 只采用明确允许再分发的目录 |
| [Education Agent Skills](https://github.com/GarethManning/education-agent-skills/tree/main/skills) | 学习方法、批判性思考 | [CC BY-SA 4.0](https://github.com/GarethManning/education-agent-skills#licence) | 保留署名和许可证链接、标明是否修改，并以相同许可证分发 |
| [Mercury Agent Skills](https://github.com/cosmicstack-labs/mercury-agent-skills) | 个人效率、职业、内容、业务 | 仓库为 [MIT](https://github.com/cosmicstack-labs/mercury-agent-skills/blob/main/LICENSE) | 选择短小、无外部副作用的 Skill |
| [OpenAI Skills Catalog](https://github.com/openai/skills) | 文档、部署、安全、研究与协作 | 逐 Skill 授权，不能把仓库内容统一视为同一许可证 | 后续按单个目录核对许可证和工具依赖 |
| [OpenAI Plugins](https://github.com/openai/plugins) | Notion、GitHub、OpenAI 开发 | 本文候选已逐项核实为 MIT 或 Apache-2.0；其他内容不自动视为可再分发 | 多数依赖 connector 或平台账号，作为条件候选 |
| [skills.sh](https://skills.sh/) | 跨仓库发现 | 聚合结果本身不代表可再分发 | 只用来发现，再回上游仓库复核 |

本轮没有从 OpenAI Skills Catalog 和 Vercel Agent Skills 选入首批内容；后续补充候选时，仍需
逐项核对许可证、工具依赖和跨 Agent 可用性。

## 3. A 组：原首轮 15 个优先实测队列

以下是测试前确定的首轮队列，不再代表最终第一批。最终入选项以上文“第一批最终选定”为准。

| 编号 | Skill | 场景 | 来源 / 许可 | 主要门槛 |
|---|---|---|---|---|
| A01 | [internal-comms](https://github.com/anthropics/skills/tree/main/skills/internal-comms) | 工作汇报、FAQ、事故通报 | Anthropic / Apache-2.0 | 无硬依赖；检查其中的组织专属假设 |
| A02 | [meeting-minutes](https://github.com/github/awesome-copilot/tree/main/skills/meeting-minutes) | 会议纪要与行动项 | Awesome Copilot / MIT | 无硬依赖；以会议文本为输入 |
| A03 | [brag-sheet](https://github.com/github/awesome-copilot/tree/main/skills/brag-sheet) | 个人成果记录、述职准备 | Awesome Copilot / MIT | 无硬依赖 |
| A04 | [convert-plaintext-to-md](https://github.com/github/awesome-copilot/tree/main/skills/convert-plaintext-to-md) | 将杂乱文本整理为 Markdown | Awesome Copilot / MIT | 无硬依赖 |
| A05 | [exam-ready](https://github.com/github/awesome-copilot/tree/main/skills/exam-ready) | 复习计划与考前检查 | Awesome Copilot / MIT | 无硬依赖；避免承诺学习结果 |
| A06 | [spaced-practice-scheduler](https://github.com/GarethManning/education-agent-skills/tree/main/skills/memory-learning-science/spaced-practice-scheduler) | 间隔复习计划 | Education Agent Skills / CC BY-SA 4.0 | 无硬依赖；按来源表要求归因和标注修改 |
| A07 | [ai-claim-checker](https://github.com/GarethManning/education-agent-skills/tree/main/skills/student-learning/ai-claim-checker) | 检查 AI 回答中的主张 | Education Agent Skills / CC BY-SA 4.0 | 按来源表要求归因；明确“核查”不等于自动证明 |
| A08 | [decision-matrix](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/creative-personal-development/decision-matrix) | 日常和工作决策比较 | Mercury / MIT | 无硬依赖 |
| A09 | [storytelling-advisor](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/creative-personal-development/storytelling-advisor) | 故事结构与表达改进 | Mercury / MIT | 无硬依赖 |
| A10 | [linkedin-post-formatter](https://github.com/github/awesome-copilot/tree/main/skills/linkedin-post-formatter) | 社交内容排版 | Awesome Copilot / MIT | 无硬依赖；只生成草稿，不自动发布 |
| A11 | [documentation-writer](https://github.com/github/awesome-copilot/tree/main/skills/documentation-writer) | 项目文档与使用说明 | Awesome Copilot / MIT | 需要用户提供项目上下文 |
| A12 | [diagram-maker](https://github.com/openclaw/openclaw/tree/main/skills/diagram-maker) | 流程图、结构图 | OpenClaw / MIT | 无账号和密钥；验证不同 Agent 的制图能力 |
| A13 | [weather](https://github.com/openclaw/openclaw/tree/main/skills/weather) | 日常和出行天气查询 | OpenClaw / MIT | 需要联网和 `curl` 或等价网页获取能力 |
| A14 | [video-frames](https://github.com/openclaw/openclaw/tree/main/skills/video-frames) | 视频抽帧与片段提取 | OpenClaw / MIT | 需要 FFmpeg |
| A15 | [frontend-design](https://github.com/anthropics/skills/tree/main/skills/frontend-design) | 高质量界面设计 | Anthropic / Apache-2.0 | 无硬依赖；检查并移除宿主产品专属表述 |

首批刻意不放旅行预订、发邮件、自动发布和云端写入类 Skill。这些操作需要账号、凭据和用户确认，
不符合第一批“低依赖、低副作用”的目标。

## 4. B 组：扩展到 40 个的实测队列

| 编号 | Skill | 场景 | 来源 / 许可 | 进入正式内置前要解决 |
|---|---|---|---|---|
| B01 | [theme-factory](https://github.com/anthropics/skills/tree/main/skills/theme-factory) | 文档和网页主题 | Anthropic / Apache-2.0 | 验证跨 Agent 输出 |
| B02 | [algorithmic-art](https://github.com/anthropics/skills/tree/main/skills/algorithmic-art) | p5.js 生成艺术 | Anthropic / Apache-2.0 | 需要浏览器和 p5.js |
| B03 | [canvas-design](https://github.com/anthropics/skills/tree/main/skills/canvas-design) | 海报与静态视觉 | Anthropic / Apache-2.0；字体另有 OFL | 包体较大，逐项核验字体许可 |
| B04 | [skill-creator](https://github.com/anthropics/skills/tree/main/skills/skill-creator) | 创建和优化 Skill | Anthropic / Apache-2.0 | 去除 Claude 专属假设并验证脚本 |
| B05 | [webapp-testing](https://github.com/anthropics/skills/tree/main/skills/webapp-testing) | 本地 Web 应用测试 | Anthropic / Apache-2.0 | Python、Playwright、浏览器 |
| B06 | [daily-standup-journal](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/creative-personal-development/daily-standup-journal) | 日报、站会整理 | Mercury / MIT | 与会议纪要候选做去重测试 |
| B07 | [time-blocking-scheduler](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/creative-personal-development/time-blocking-scheduler) | 个人时间块安排 | Mercury / MIT | 只生成计划，不直接写日历 |
| B08 | [resume-writing](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/career/resume-writing) | 简历撰写 | Mercury / MIT | 验证不同职位和语言 |
| B09 | [interview-prep](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/career/interview-prep) | 求职面试准备 | Mercury / MIT | 避免虚构经历 |
| B10 | [retrieval-practice-generator](https://github.com/GarethManning/education-agent-skills/tree/main/skills/memory-learning-science/retrieval-practice-generator) | 主动回忆练习 | Education Agent Skills / CC BY-SA 4.0 | 按来源表要求归因、标注修改并同许可分发 |
| B11 | [study-strategy-selector](https://github.com/GarethManning/education-agent-skills/tree/main/skills/self-regulated-learning/study-strategy-selector) | 选择学习策略 | Education Agent Skills / CC BY-SA 4.0 | 按来源表要求归因、标注修改并同许可分发 |
| B12 | [research-planner](https://github.com/NKZ55/research-planner/tree/main/skills/research-planner) | 研究问题与计划 | NKZ55 / MIT | 核验引用和检索假设 |
| B13 | [md-to-docx](https://github.com/github/awesome-copilot/tree/main/skills/md-to-docx) | Markdown 转 Word | Awesome Copilot / MIT | Node.js 18+、`docx>=9`、`marked>=15` |
| B14 | [convert-word-to-md](https://github.com/github/awesome-copilot/tree/main/skills/convert-word-to-md) | Word 转 Markdown | Awesome Copilot / MIT | Python、`markitdown[docx]>=0.1.0` |
| B15 | [convert-pdf-to-md](https://github.com/github/awesome-copilot/tree/main/skills/convert-pdf-to-md) | PDF 转 Markdown | Awesome Copilot / MIT | Python、`markitdown[pdf]>=0.1.0`、`pymupdf>=1.24.0`；扫描件另配 OCR |
| B16 | [convert-excel-to-md](https://github.com/github/awesome-copilot/tree/main/skills/convert-excel-to-md) | Excel 转 Markdown | Awesome Copilot / MIT | Python、`markitdown[xlsx]>=0.1.0` |
| B17 | [markdown-to-html](https://github.com/github/awesome-copilot/tree/main/skills/markdown-to-html) | Markdown 转网页 | Awesome Copilot / MIT | 核验脚本和 HTML 安全 |
| B18 | [ad-campaign-analyzer](https://github.com/github/awesome-copilot/tree/main/skills/ad-campaign-analyzer) | 广告活动分析 | Awesome Copilot / MIT | 需要用户提供数据；避免外部自动投放 |
| B19 | [gtm-positioning-strategy](https://github.com/github/awesome-copilot/tree/main/skills/gtm-positioning-strategy) | 产品定位 | Awesome Copilot / MIT | 无硬依赖；验证输出不空泛 |
| B20 | [competitor-ad-intelligence](https://github.com/github/awesome-copilot/tree/main/skills/competitor-ad-intelligence) | 竞品广告研究 | Awesome Copilot / MIT | 需要联网；遵守目标站点条款 |
| B21 | [blogwatcher](https://github.com/openclaw/openclaw/tree/main/skills/blogwatcher) | 博客和 RSS 更新追踪 | OpenClaw / MIT | 需要 `blogwatcher` CLI |
| B22 | [openai-whisper](https://github.com/openclaw/openclaw/tree/main/skills/openai-whisper) | 本地音频转写 | OpenClaw / MIT | 本地 Whisper、模型下载和算力 |
| B23 | [songsee](https://github.com/openclaw/openclaw/tree/main/skills/songsee) | 音频可视化 | OpenClaw / MIT | 需要对应 CLI；验证跨平台 |
| B24 | [meeting-note-summarizer](https://github.com/cosmicstack-labs/mercury-agent-skills/tree/main/categories/creative-personal-development/meeting-note-summarizer) | 将会议笔记整理成摘要和行动项 | Mercury / MIT | 与 A02 做去重测试，只保留效果更好的一个 |
| B25 | [travel-planner](https://github.com/ailabs-393/ai-labs-claude-skills/tree/main/packages/skills/travel-planner) | 旅行行程规划 | AI Labs / MIT | Python 3、联网；移除写入 `~/.claude/travel_planner/` 的持久化逻辑后再测 |

B25 的核心行程生成实测失败，并会把敏感旅行画像写入宿主目录，首轮结论为 `reject`。旅行场景
另找质量更高的候选，不再以 B25 作为适配起点。

## 5. C 组：20 个条件候选

这些 Skill 只有在依赖、授权或交互边界解决后才进入 A/B 组。

| 编号 | Skill | 暂缓原因 |
|---|---|---|
| C01 | [notion-knowledge-capture](https://github.com/openai/plugins/tree/main/plugins/notion/skills/notion-knowledge-capture) | MIT；依赖 Notion connector、账号和工作区 |
| C02 | [notion-meeting-intelligence](https://github.com/openai/plugins/tree/main/plugins/notion/skills/notion-meeting-intelligence) | MIT；依赖 Notion connector 和账号 |
| C03 | [notion-research-documentation](https://github.com/openai/plugins/tree/main/plugins/notion/skills/notion-research-documentation) | MIT；依赖 Notion connector 和账号 |
| C04 | [notion-spec-to-implementation](https://github.com/openai/plugins/tree/main/plugins/notion/skills/notion-spec-to-implementation) | MIT；依赖 Notion connector 和项目上下文 |
| C05 | [gh-address-comments](https://github.com/openai/plugins/tree/main/plugins/github/skills/gh-address-comments) | Apache-2.0；需要 `gh`、GitHub 登录和本地仓库 |
| C06 | [gh-fix-ci](https://github.com/openai/plugins/tree/main/plugins/github/skills/gh-fix-ci) | Apache-2.0；需要 `gh`、Actions 和本地构建环境 |
| C07 | [mcp-builder](https://github.com/anthropics/skills/tree/main/skills/mcp-builder) | Apache-2.0；需要 Python/Node，并含 Claude 专属假设 |
| C08 | [slack-gif-creator](https://github.com/anthropics/skills/tree/main/skills/slack-gif-creator) | Apache-2.0；需要 Python、Pillow、imageio、FFmpeg、NumPy |
| C09 | [deploy-to-vercel](https://github.com/vercel-labs/agent-skills/tree/main/skills/deploy-to-vercel) | 有外部部署写操作；仓库许可证文件待补 |
| C10 | [vercel-react-best-practices](https://github.com/vercel-labs/agent-skills/tree/main/skills/react-best-practices) | 内容适合内置，但仓库许可证文件待补 |
| C11 | [writing-guidelines](https://github.com/vercel-labs/agent-skills/tree/main/skills/writing-guidelines) | 运行时读取远端规则；仓库许可证文件待补 |
| C12 | [summarize](https://github.com/openclaw/openclaw/tree/main/skills/summarize) | 需要 `summarize` CLI 及 OpenAI、Anthropic、xAI 或 Gemini 等模型 API key |
| C13 | [goplaces](https://github.com/openclaw/openclaw/tree/main/skills/goplaces) | 需要 CLI、Google Places API 凭据和已启用计费的云项目，会产生费用 |
| C14 | [obsidian](https://github.com/openclaw/openclaw/tree/main/skills/obsidian) | 需要 Obsidian 1.12.7+、官方 CLI 和运行中的桌面应用，并会创建、编辑、移动或删除本地笔记 |
| C15 | [notion](https://github.com/openclaw/openclaw/tree/main/skills/notion) | 需要 token 或 `ntn login`，可读取、更新、上传和删除工作区内容 |
| C16 | [apple-notes](https://github.com/openclaw/openclaw/tree/main/skills/apple-notes) | 仅 macOS，需要 Automation 权限，并可编辑、移动、导出或删除私人笔记 |
| C17 | [apple-reminders](https://github.com/openclaw/openclaw/tree/main/skills/apple-reminders) | 仅 macOS，并会修改提醒事项 |
| C18 | [trello](https://github.com/openclaw/openclaw/tree/main/skills/trello) | 需要 Trello 账号和 API 凭据，并有外部写操作 |
| C19 | [spotify-player](https://github.com/openclaw/openclaw/tree/main/skills/spotify-player) | 需要 Spotify Premium 和 `spogo`，推荐认证方式会导入浏览器 Cookie |
| C20 | [openai-whisper-api](https://github.com/openclaw/openclaw/tree/main/skills/openai-whisper-api) | 需要 API key、会产生费用，并会把录音上传到外部 OpenAI 或兼容 API |

## 6. D 组：10 个明确不纳入

| 编号 | Skill | 不纳入原因 |
|---|---|---|
| D01 | [yeet](https://github.com/openai/plugins/tree/main/plugins/github/skills/yeet) | 默认执行提交、推送和创建 PR，外部写操作过强 |
| D02 | [chatgpt-app-submission](https://github.com/openai/plugins/tree/main/plugins/openai-developers/skills/chatgpt-app-submission) | 平台专属，并依赖经常变化的提交流程 |
| D03 | [docx](https://github.com/anthropics/skills/tree/main/skills/docx) | All Rights Reserved，不允许作为开源内置包再分发 |
| D04 | [pdf](https://github.com/anthropics/skills/tree/main/skills/pdf) | All Rights Reserved，不允许作为开源内置包再分发 |
| D05 | [pptx](https://github.com/anthropics/skills/tree/main/skills/pptx) | All Rights Reserved，不允许作为开源内置包再分发 |
| D06 | [xlsx](https://github.com/anthropics/skills/tree/main/skills/xlsx) | All Rights Reserved，不允许作为开源内置包再分发 |
| D07 | [vercel-cli-with-tokens](https://github.com/vercel-labs/agent-skills/tree/main/skills/vercel-cli-with-tokens) | 直接处理云平台 token，并带外部写操作 |
| D08 | [taskflow](https://github.com/openclaw/openclaw/tree/main/skills/taskflow) | 强绑定 OpenClaw 的 `api.runtime.tasks.flow`、ACP 和 session 语义，不能通用移植 |
| D09 | [taskflow-inbox-triage](https://github.com/openclaw/openclaw/tree/main/skills/taskflow-inbox-triage) | 强绑定 OpenClaw 运行时，且主要是包含 Slack 路由的特定示例 |
| D10 | [camsnap](https://github.com/openclaw/openclaw/tree/main/skills/camsnap) | 摄像头访问涉及隐私和设备权限，不适合作为默认内容 |

## 7. 仍然缺的场景

公开候选最弱的是“无需账号即可使用”的日常生活、旅行和活动 Skill。市场搜索能找到不少旅行
规划器，但多数依赖 API、账号或宿主持久化目录。与其硬凑，建议由 SkillHub 基于可复用框架维护
四个小而明确的 Skill，再分别与公开候选做效果对比：

- `trip-planning-brief`：只做目的地、日期、预算、偏好和节奏规划，不预订。
- `packing-checklist`：按天气、天数、活动和人群生成可勾选行李清单。
- `event-planner`：生成聚会或活动的时间线、物料、预算和应急清单。
- `meal-and-grocery-planner`：按人数、饮食限制和预算生成菜单与购物清单。

这四个只有在完成内容、测试和许可声明后才计数，不能先用名字占满 30～50 个名额。

## 8. 实际入库清单

每个 A/B 候选必须逐项完成：

1. 固定到上游 commit SHA，保存来源、作者和许可证文本。
2. 检查 `SKILL.md` frontmatter、脚本、二进制资源、下载行为、凭据和文件访问。
3. 只做必要适配；修改后标明为衍生版本，不冒充上游原作。
4. 在声明支持的 Agent 中完成至少一个真实任务，并记录输入、预期结果和实际结果。
5. 运行 SkillHub 包校验、安全扫描和人工审查。
6. 使用新版本号打包，上传不可变的官方 CDN URL。
7. 将不可变 URL 和制品 SHA-256 写入
   `server/skillhub-app/src/main/resources/builtin-skills/manifest.json`，在干净部署中验证同步、下载和安装。

第一批 15 个适配包已经完成内容、安全和确定性构建检查，并已通过官方不可变 CDN URL 和
SHA-256 写入运行时 manifest。后续更新必须发布新版本和新制品，不得覆盖当前 URL 对应的字节。
