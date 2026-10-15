# cocofhu/skillhub 插件参考

核对日期：2026-10-12。用户提供 https://www.npmjs.com/package/@cocofhu/skillhub 作为体验参考，并确认 Q9 A。

## 来源与核验

npm 页面搜索访问受限，改读 npm Registry 元数据和 GitHub 源码，未安装或执行第三方插件。
官方 Registry 的 @cocofhu/skillhub/latest 返回 0.2.16；仓库 main 的 package.json 名称为 skillhub-plugin，版本为 0.2.16。
README 推荐 skillhub-plugin，保留 @cocofhu/skillhub 旧包名的安装说明，并明确声明与 SkillHub、DeepSeek 没有从属或背书关系。
因此这是面向 skillhub.cn 的社区实现参考，不据此声称腾讯官方维护。

## 已观察的实现

- 包声明 dsh.bundle.patch 和 dsh.client，分别导出 Host 与 Client。
- Host 注册技能搜索、安装、已安装列表、卸载工具，并提供模型使用说明。
- 技能安装模块直接请求 HTTP 下载接口并解包写入技能目录；不以用户手动安装 SkillHub CLI 为前提。
- README 描述设置页、对话技能卡片、详情、独立浏览面板和已安装视图；界面未实际运行核验。
- 还包含 DSH 插件目录与插件安装功能。这是额外资产范围，不自动纳入我们的需求。
- README 的设置页“更新”指连接插件自身更新，不能当作已具备技能升级管理的证明。
- Host 源码仍使用 settings.register 风格接口；必须按所选 DSH 版本核验，不能直接假定与最新设置接口兼容。

## 对本任务的影响

### Q11 / Q12 源码复核

- listInstalled(skillsDir) 只读取一个配置目录的直接子目录，要求存在 SKILL.md；默认根为 $DSH_HOME/skills。它既不限于自己安装的技能，也不汇总 DSH 的全部发现来源。
- 列表解析 SKILL.md 的 name/description/version，并统计文件数、大小和修改时间；这条路径不检查 Registry/命名空间/安装器归属。
- uninstallSkill 检查目标位于配置根下且含 SKILL.md 后删除目录，未在该函数中校验安装来源。
- installSkill 先解包到临时目录，然后删除同 slug 目标目录并替换；该函数未检查本地修改或来源冲突。这是安装函数的行为，不代表已审计所有上层确认流程。
- 搜索通过 skillhub_search 的工具描述以及 systemPrompt section 引导模型：用户要找、推荐、浏览技能时使用专用工具，提取关键词，按 offset 续查；用户选择后再安装。它不是每个业务任务前都自动搜索的确定性调度器。
- 对应本任务：Q11 B 的全部可发现技能展示及来源识别需要额外实现；Q12 A 与其提示词表达的触发意图接近，但提示词不是可靠性保证，仍需验收模型实际行为。

确认目标交付形态为一个可安装的 DSH 包：安装后具备模型工具和图形界面，不要求用户另装 SkillHub CLI。
源码仍按用户决定放在本仓库 plugins/dsh/ 候选目录，单独打包。
参考其交互和 Host/Client 分工；实际接口、命名空间、认证、安装记录和升级冲突处理使用本项目契约。
不自动扩展到 DSH 插件市场、第三方插件安装、插件自身更新或 DSH 自动重启。
该仓库的 MIT 声明不代表本轮已决定复制代码；尚未复制第三方实现。

## 主要链接

- [README](https://github.com/cocofhu/skillhub/blob/main/README.md)
- [包清单](https://github.com/cocofhu/skillhub/blob/main/package.json)
- [Host](https://github.com/cocofhu/skillhub/blob/main/src/host.ts)
- [技能安装](https://github.com/cocofhu/skillhub/blob/main/src/install.ts)
- [旧包 Registry 元数据](https://registry.npmjs.org/@cocofhu%2fskillhub/latest)

本记录中的 main/latest 为当日观察，实施时应另锁定所参考版本。
