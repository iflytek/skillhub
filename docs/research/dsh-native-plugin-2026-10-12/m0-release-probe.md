# M0：DSH 发行产物与 Remote 生成验证

## 结论

`@deepseek-ai/dsh@0.2.0-rc.2` 具备方案需要的 Bundle、Client、Remote 和主要 UI 插槽。首轮独立插件生成遇到官方生成器对工作区结构的假设；通过构建期注册官方协议声明、限定生成目标，已生成最小 Host/Remote 产物，无需切换 Alpha、手写协议或修改官方类型。

本次证明发行接口存在、官方生成器可输出独立插件产物及生成的 codec 可执行；尚不证明完整 DSH Profile 加载、浏览器挂载或真实调用通过。

## 发行依赖

从 npm 官方 registry 下载精确版本元数据和 tarball，未执行包安装脚本。主包依赖 `dsh-base`、`dsh-web-app` 等同为 `0.2.0-rc.2`；Cordis 声明为 `~4.0.4`，本探针固定 `4.0.4`。

| 包（均在 `@deepseek-ai/` scope） | 已核实产物 |
| --- | --- |
| dsh | CLI 入口 `lib/bin.js`，同版本依赖清单 |
| dsh-web-app / dsh-base | Web 与基础依赖组合、patch 文件 |
| dsh-typert-protocol | `TypertRemoteService`、标准装饰器 `Remote`、`RemoteResult`、Client `$mount` 类型 |
| dsh-typert-generator | 主入口 `WorkspaceTypertGenerator`；`./tsdown` 导出 `typertPlugin`，没有 CLI bin |
| dsh-api-gateway | Host 与 Client 入口 |
| dsh-typert-registry / dsh-api-remotes | 注册表和客户端装配包 |
| dsh-client-ui-plugin-manager | `./typert`、`./remote` 实际生成文件，`plugins.bundle.config` 插槽 |
| dsh-client-ui-sidebar / dsh-client-ui-layout | `sidebar.panellist`、`selectPanel(MainPanelId \| null)` |
| dsh-plugin-manager | Bundle 管理器及 Host/Remote 生成文件 |

声明存在性与包内容检查不能代替运行时验收。构建依赖不应分别跟随 `latest`。

## 独立生成失败原因

直接使用 npm 安装的 protocol，在插件中声明 `TypertRemoteService` 和 `@Remote ping()`，生成报错：

```text
TypertAnalysisError: typert(host): @skillhub/m0-probe publishes Remote artifacts but has no Remote methods
```

发行生成器 `lib/types/analyzer.js` 的两个限制：

1. `loadRegistrations()` 只接纳工作区 `packages/` 下的项目（特定分析可包含 `vendor/`）；普通 `node_modules` 不成为注册项目。
2. `isTypeMetaSymbol()` 通过已注册 protocol 项目或 ambient module 声明识别 Remote 符号；直接 npm 类型引用未命中。

已比对方案参考源码中的同名实现，它也保留该识别方式。因此不能假定切到参考源码对应版本就能解决。

## 已验证的构建装配方式

在隔离临时工作区完成以下步骤：

1. 将官方 protocol tarball 的 `lib/types` 原字节复制到临时 `packages/protocol/declarations`。
2. 临时协议项目保留官方包名，通过临时 manifest 指向这些声明，增加仅供分析的 tsconfig；不修改类型内容、运行时代码或线上包。
3. `tsconfig.host.json` aggregate 注册插件和该协议项目；aggregate 和插件 tsconfig 的 paths 都指向暂存的官方声明。
4. 调用官方 `WorkspaceTypertGenerator.generate(['@skillhub/m0-probe'], ['host'])`，限定本插件。不能用默认全量生成：它会继续分析协议项目并触发类型分析异常。
5. 将返回的 Host 和 Remote JS/声明产物写入插件 `lib`。生成产物仍引用官方运行依赖，不携带另一份协议运行时。

暂存目录避免使用 `lib/types` 作为声明导出路径，因为生成器会把该形态反向映射到 `src`，而 npm tarball 不带对应源码。

此方式属于构建工具装配；生产插件必须由可重复脚本自动生成暂存区，校验来源版本，并在干净环境重新验证。不能将临时声明项目当作另一个需要发布的官方包。

## 实测结果

环境：Node `24.17.0`，pnpm `10.33.0`。隔离目录：`/tmp/skillhub-dsh-m0-release-probe`。

```bash
pnpm --dir /tmp/skillhub-dsh-m0-release-probe/runtime add --ignore-scripts --save-exact \
  @deepseek-ai/dsh-typert-generator@0.2.0-rc.2 \
  @deepseek-ai/dsh-typert-protocol@0.2.0-rc.2 \
  @deepseek-ai/cordis@4.0.4 zod@4.4.3
cd /tmp/skillhub-dsh-m0-release-probe/runtime
node generate.mjs
```

结果：

- 官方三个入口导入成功：`TypertRemoteService`、`Remote`、`typertPlugin`。
- 定向生成成功：`package=@skillhub/m0-probe`，`face=host`，`remote=true`。
- 生成的 `typert.host.js` 和 `typert.remote-client.js` 导入成功。
- Remote descriptor 中方法名为 `ping`，返回 codec 接受字符串 `pong`、拒绝数字 `42`。
- 暂存的官方声明树与 tarball 原内容逐字节相同，共 10 个文件。

未读取或修改用户 DSH_HOME、profiles、技能目录；未安装真实技能、调用模型或发布插件。

## 下一步验收

1. 将构建暂存与定向生成固化到插件 M0 脚本，覆盖干净 tarball 构建。
2. 在隔离 Profile 验证 Host 注册、Client `$mount`、实际 Remote 调用及注销。
3. 验证双入口 UI、可信用户/项目上下文、技能发现观察后，再决定是否将 rc.2 从候选提升为支持基线。

官方元数据：[DSH 精确版本](https://registry.npmjs.org/@deepseek-ai%2fdsh/0.2.0-rc.2)、[生成器精确版本](https://registry.npmjs.org/@deepseek-ai%2fdsh-typert-generator/0.2.0-rc.2)、[协议精确版本](https://registry.npmjs.org/@deepseek-ai%2fdsh-typert-protocol/0.2.0-rc.2)。
