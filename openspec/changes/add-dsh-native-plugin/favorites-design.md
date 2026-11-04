# 本地收藏与当前来源筛选（Issue #1064）

## 目标与交互

帮助用户保存稍后查看的市场技能，不要求当场安装。沿用现有 shadcn/Radix 适配组件：市场卡片标题行、市场详情标题操作区各一个小书签按钮，明确 aria-label/title/aria-pressed，点击不穿透至详情；不增加一排文字操作。浏览技能页提供“仅看收藏”切换，读取当前来源的完整收藏列表并本地搜索，不能只筛选已加载的市场分页。空态简洁，错误可重试。收藏不代表安装或服务端收藏数，不增加登录、置顶、最近使用或统计。

收藏卡不展示快照版本为最新。用户点击该项详情或安装时，仅解析这一项当前可下载版本，再走既有精确版本详情和安装确认；不批量后台解析收藏、不静默装旧快照。离线或下架时保留收藏，仍可取消。详情的官网链接保留。

## 存储与边界

存储在 canonical DSH_HOME 下 `skillhub/favorites.json`，独立于inventory v2与设置草稿；相同DSH_HOME的Profile/项目共享，不提供收藏安装范围选择，不同DSH_HOME隔离。身份为规范化Registry来源+namespace+slug，名称只是快照，版本不进入身份。切源只显示新来源收藏，保留旧来源记录。

文件schema为 `{version:1,items:[{registry,namespace,slug,displayName?,summary?}]}`。不存凭证、安装令牌、任务或对话内容。坐标各最多256字符，展示名称256、摘要1024、规范化来源2048字符；全文件最多500项、8MiB。展示快照可在Client按长度截取，Host仍须校验。超限明确报错，不静默丢弃已有记录。未知schema、损坏JSON、非普通文件、数据目录内symlink拒绝覆盖，不自动重置；不存在文件视为空。

复用官方 `@deepseek-ai/dsh-atomic-write` 的withFileLock与writeFileAtomic，显式声明三个支持RC的peer和锁定开发SDK，不借用未声明传递依赖。目录新建0700、文件0600；完整读→校验→合并→原子写在同一个跨进程锁中，等待最多2000ms。三个RC共同使用waitMs参数；旧RC2不承诺等锁时立即取消，拿锁后、原子写受理前重新核验来源revision和取消；开始调用writeFileAtomic即受理，受理后切源允许完成原来源的写入，不补写新来源，迟到结果不进入新源UI。相同明确目标状态幂等，不使用反转式toggle。不同技能并发收藏不丢更新；共享文件仅支持同机同PID命名空间，不承诺网络盘跨主机写入。

收藏依赖/存储失败仅影响收藏，不扩大现有安装库存写门禁；不触碰技能文件、CLI共享库存或服务端收藏API。读取也校验文件类型与大小；错误只返回稳定代码，不透出路径或原始文件内容。

## Host RPC与Client状态

- `listFavorites(sourceRevision,signal): FavoriteList`：返回当前规范化来源全部展示快照，不发Registry HTTP。
- `setFavorite(sourceRevision,item:FavoriteSkill,favorite:boolean,signal): FavoriteList`：以当前来源为准保存明确状态，锁内重验来源/取消，成功后返回该来源列表，不发Registry HTTP。
- `resolveFavoriteVersion(sourceRevision,namespace,slug,signal): FavoriteVersion`：只读调用现有Registry.resolve省略version，返回坐标与明确版本，不返回下载URL/令牌；然后由既有详情重新核验，快照不授予安装权限。

复用readSource及生成Remote，三个方法是本机用户偏好/只读入口，不假冒agent范围文件安装权限。类型在host/types.ts维护，生成绑定不手改。存储错误统一 `skillhub/favorites-error`，reason为INVALID_INPUT、UNAVAILABLE、INVALID_STORE、LIMIT_REACHED；来源及Registry错误复用原契约。

Query缓存按sourceRevision隔离；来源切换后的旧查询、保存或解析结果不能进入新来源UI。成功持久化后再呈现保存成功；多次交互/多控件写入需避免返回旧全量列表覆盖新状态。保存失败保留原状态并提供重试，不能阻断市场浏览。收藏筛选期间不请求市场分页；切回后保留市场查询/排序/标签语义。

## 验收

正常：市场收藏→详情同态→完整收藏列表→重启读取→取消，不改变安装、库存或服务端starCount。变更：跨分页与本地搜索、两来源同名隔离、迟到响应/取消、最新版本解析、下架/离线保留、跨进程RMW、未知/损坏/超限/symlink不覆盖。以独立磁盘字节、HTTP记录、子进程屏障和真实DOM作为Oracle，不只mock返回值。

构建/类型/lint/测试、生成端点和打包检查；三个已支持RC至少实测收藏读写、来源隔离、重启和最新解析。RC5补浏览器完整流程、深浅主题与390px、键盘/焦点和卡片点击边界。CI、源码、真实Host、人工、合并与发布分开记录，不发送真实模型或发布npm。
