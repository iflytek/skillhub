# DSH 插件开发与同仓库分发调研

2026-10-12；需求澄清用事实记录，不替代后续 OpenSpec。

## 仓库与安装包是两个边界

DSH 安装的是声明了 bundle 的包，不要求独立 Git 仓库。官方 CLI 支持本地目录、npm 包等安装来源。
因此可在 SkillHub 的 plugins/dsh/ 开发独立 Node/TypeScript 包；该路径为建议，尚未创建。
SkillHub 的 cli/package.json、.github/workflows/pr-cli.yml 和 .github/workflows/release-cli.yml 已展示同仓库独立测试、发布的先例。
插件 UI 运行在 DSH 客户端；不是往 SkillHub 的 web/ 增加页面。插件 Host 运行在 DSH 所在主机，通过 SkillHub 接口访问 Registry。

## 开发顺序

1. 固定 DSH 兼容版本或提交，验证本机和远程单用户的目标运行环境。
2. 在模块目录创建独立包，声明模块入口、依赖、发布文件和 dsh.bundle.patch。
3. 使用 cordis.patch.yml 组合 Host 和可选 Client 插件。
4. Host 注册模型工具、配置和 SkillHub 操作；Client 注册连接设置、浏览管理页面和工具卡片。
5. 构建 Node 入口及浏览器产物，在隔离的 DSH 环境通过本地包目录安装验证。
6. 完成授权、搜索、完整包安装、DSH 加载、升级卸载和异常路径验收。
7. 验证打包产物在干净环境可安装；之后按单独发布流程分发插件包。

调研时尚未确定底层复用方式；后续已在 [OpenSpec 详细设计](../../../openspec/changes/add-dsh-native-plugin/design.md)选定 CLI 内部消费入口随插件构建的方案，以该设计为准。

## 本地联调命令形态

模块创建并构建后，可从 SkillHub 仓库根目录安装到测试用 DSH Web profile：

```sh
dsh plugin --profile web add ./plugins/dsh
```

该命令尚未执行。远程 DSH 的本地路径相对于服务器，不是访问页面的电脑。
用户安装发布产物时使用实际发布包名或 tarball；包名尚未确定，不提供虚构的可安装包。
不要直接把整个 SkillHub Git 仓库当作插件包安装，仓库根目录不是插件包根目录。

## 关键事实

- bundle 清单通过 dsh.bundle.patch 声明补丁文件；DSH Launcher 将 bundle 补丁组合到 profile。
- 相对本地目录的安装路径以发起命令的目录为准。
- 插件管理 UI 也支持第三方包及本地路径安装，不强制用户手工编辑 profile 文件。
- Host 工具的 presentCall/presentResult 不会自动变成当前 DSH Web 的专用卡片；Web Client 通过 tool.call.toolview 注册对应工具的呈现。
- 配置服务与可用的业务页面是不同层次；声明 Config 不等于自动拥有完整 SkillHub 设置界面。
- 插件管理页提供 plugins.bundle.config 和 plugins.row.config 等配置挂载点。可复用其容器与 Configure 入口，自行提供 SkillHub 配置组件；概念图中的独立设置入口需按最终宿主挂载方式调整。

## 官方依据

- [插件发布教程，固定提交](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/docs/user/develop/basic/publish.md)
- [CLI reference](https://github.com/deepseek-ai/deepseek-harness/blob/master/apps/cli/reference/README.md)
- [Bundle packages](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/bundle/README.md)
- [Plugin Manager](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/boot/plugin-manager/README.md)
- [Plugin Manager UI](https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/client/ui-plugin-manager/README.md)
- [Tool authoring，已读取固定提交全文](https://github.com/deepseek-ai/deepseek-harness/blob/d743267388641bc76f17c45ce8b4c231aed1d32c/docs/cookbook/adding-a-tool.md)

搜索同时覆盖社区教程；技术结论以官方源码和文档为准。master 链接可能变化，实施前需再固定全部依赖和构建基线。
本轮只阅读文档、源码及现有 CI，未构建、安装或发布插件。

官方 workspace package 教程的共享构建 preset 属于 DSH 自身仓库，不可直接复制其路径到 SkillHub；外部插件客户端构建配置仍需验证。
