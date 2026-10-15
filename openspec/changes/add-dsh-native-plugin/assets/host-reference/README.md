# DSH 宿主界面依据

核对日期：2026-10-12。官方源码提交：`d743267388641bc76f17c45ce8b4c231aed1d32c`。

## 视觉来源

- [官方 onboarding 素材](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/client/ui-settings-account/src/client/assets/onboarding-welcome-zh.png)：本目录 dsh-official-onboarding.png。用于核对品牌、工作区/会话树与输入框关系；其中桌面装饰不作为 Web 要求。
- [官方设置截图](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/docs/user/guide/providers-models-page.zh.png)：本目录 dsh-official-settings.png。仅作控件与视觉参考；SkillHub 配置实际进入插件详情，不进入模型设置。
- [摩尔线程实机教程](https://docs.mthreads.com/playbook/playbook-doc-online/deepseek_harness/)中的 [Web 截图](https://docs.mthreads.com/assets/images/17_deepseek_harness_workspace-903c3d9148f515e522ba9d90353c1fa8.png)：v3 图像编辑的主参考。该截图属于第三方观察，不代表当前所有版本；接口事实以官方源码为准。原图未复制进仓库，生成图为在该截图基础上的设计演示，来源保留于此。

官方仓库素材随附其 [MIT 许可证](DSH-LICENSE.txt)。品牌名称/图形仅用于说明宿主，不表示背书。

## 扩展点证据

| 产品位置 | 官方依据 | 本插件增量 |
| --- | --- | --- |
| 左侧工作区和会话树 | [ui-sidebar](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/client/ui-sidebar/README.md) | 原样保留，使用 sidebar.panellist 增加一个 SkillHub 入口 |
| 主内容区 | [ui-layout](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/client/ui-layout/README.md) | 与入口相同 id 的 main 面板；返回 Conversation 不改变当前 Session |
| 插件详情配置 | [ui-plugin-manager](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/client/ui-plugin-manager/README.md)及 [注册源码](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/packages/client/ui-plugin-manager/src/client/index.ts) | plugins.bundle.config 中提供 Registry 与可选详情 Tab 配置（本期无认证表单）；宿主承担插件启停/卸载 |
| 对话消息流 | [官方工具教程](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/docs/cookbook/adding-a-tool.md) | 历史全量方案中的 tool.call.toolview；当前首版不实现对话工具卡片 |

## 证据限制

官方官网在本次网络访问被拦截；改用官方 Git 仓库文档、素材及第三方实机图。没有运行 DSH，也没有以图像生成结果验证组件、点击或会话绑定行为。图像中的浏览器地址仅为原截图本地环境，不代表此项目启动了服务。
