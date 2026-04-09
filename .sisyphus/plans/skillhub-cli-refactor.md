# skillhub-cli 重构方案

## 一、现状分析

### 1.1 命令统计

| CLI | 命令数 | 说明 |
|-----|-------|------|
| skillhub-cli | 30 | 含子命令组 |
| vercel-labs/skills | 9 | 含 experimental 命令 |
| clawhub | ~25+ | 含多级子命令 |

### 1.2 skillhub-cli 当前命令列表

```
顶层命令 (30个):
login, logout, whoami, publish, search, namespaces, add, install,
download, list, remove, star, info, init, me (子命令: skills/stars),
reviews (子命令: my), notifications (子命令: list/read/read-all),
delete, versions, report, resolve, rating, rate, archive, update,
uninstall, sync, inspect, explore, transfer, hide (子命令: unhide)

全局选项: --registry, --no-input, --json
```

### 1.3 问题识别

#### 问题1: remove vs uninstall 重复
```typescript
// remove.ts 和 uninstall.ts 实现几乎完全相同
// 都扫描 agent.projectPath 和 agent.globalPath
// 都调用 removeDir 删除目录
// 唯一的细微差别: remove 的确认提示更详细
```
**结论**: 合并为一个命令，建议保留 `uninstall`（更直观）

#### 问题2: add vs install 语义模糊
```
add:    从仓库/URL/本地路径安装 (git clone + discoverSkills + installSkill)
install: 从 registry 安装 (download zip + discoverSkills + installSkill)

核心逻辑相同: discoverSkills + installSkill
区别仅在于获取 skill 源的方式
```
**结论**: 合并，`install` 作为主命令，`add` 作为 alias。`install` 自动检测源类型：
- 如果是 `owner/repo` 或 GitHub URL → 调用 git clone
- 如果是 skill slug → 从 registry 下载

#### 问题3: info vs inspect 重复
```
info:   调用 skillDetail API，显示基本信息
inspect: 调用 skillDetail API，但会搜索所有 accessible namespaces

inspect 功能是 info 的超集
```
**结论**: 合并，`inspect` 作为主命令。`info` 作为 alias

#### 问题4: delete 是 registry 操作，命名正确
```
delete <slug>: DELETE /api/v1/skills/{namespace}/{slug}
这是删除 registry 上的 skill，不是本地操作
```
**结论**: 保持不变，但考虑添加 `unpublish` 作为 alias（更直观）

#### 问题5: --namespace 默认值不一致
```
当前: 大多数命令默认 "global"
期望: 默认搜索所有 accessible namespaces，仅在明确指定时才限制
```
**结论**: 修改默认行为，使用空字符串表示"所有空间"

### 1.4 vercel-labs/skills 优秀设计

#### 交互式 find (fzf 风格)
```
- Raw readline + raw mode 实现终端选择
- 实时 debounced API 搜索 (150-350ms 自适应)
- 上下箭头导航，回车选择，ESC 取消
- 显示安装数量 (格式: 1.2K, 3.5M)
- 非交互模式: 直接输出搜索结果
```

#### 交互式 add (多选)
```
- 使用 @clack/prompts 库
- Skill 多选: multiselect
- Agent 多选: searchMultiselect (带 fuzzy 搜索)
- 安装前显示摘要 (skill → agents 映射)
- 确认提示后才执行
- Symlink vs Copy 模式选择
```

#### remove 交互式
```
- multiselect 选择要删除的 skill
- --all 一键删除所有
- 按 source 分组显示结果
```

---

## 二、重构方案

### 2.1 命令合并

| 原命令 | 新命令 | 说明 |
|--------|--------|------|
| `remove`, `uninstall` | `uninstall` | 合并，本地卸载 |
| `add` | `install` (alias) | 合并，统一安装 |
| `info` | `inspect` (alias) | 合并 |
| `delete` | `delete`, `unpublish` (alias) | 保持，添加 alias |

### 2.2 新命令体系 (23个顶层命令)

```
认证:
  login [--token] [--registry]     登录
  logout                             登出
  whoami                             显示当前用户

发现:
  search <query...> [-n/--limit]    搜索 skills
  explore [-n/--limit]               浏览最新 skills
  find [query]                       交互式搜索 (fzf 风格)
  inspect <slug> [--namespace]       查看 skill 详情
  info <slug>                        inspect 的 alias

安装/卸载:
  install <source> [-a] [-g] [--copy] [--list]  安装 (合并 add+install)
  add <source>                       install 的 alias
  uninstall <name> [-g] [--all]      卸载本地 skill (合并 remove+uninstall)
  remove <name>                      uninstall 的 alias

发布:
  publish [path] [--namespace] [--version] [--tags]  发布 skill
  sync [path] [--namespace] [--dry-run]              批量发布

版本管理:
  versions <slug>                    列出版本
  update [slug] [--all] [--dry-run]  更新 skill (完整实现)
  resolve <slug> [--version]         解析版本

社交:
  star <slug> [--unstar]             收藏
  rate <slug> <score>                评分 (1-5)
  rating <slug>                       查看评分

治理:
  archive <slug>                      归档
  hide <slug>                        隐藏 (admin)
  unhide <slug>                      取消隐藏 (admin)
  transfer <namespace> <userId>      转让所有权
  delete <slug>, unpublish <slug>    删除 published skill

命名空间:
  namespaces                          列出可访问的命名空间
  init [name]                        创建 SKILL.md 模板

其他:
  list, ls [-g]                      列出已安装 skills
  download <slug> [--version]        下载 skill 包
  report <slug> [--reason]           举报
  me skills                          我发布的 skills
  me stars                           我收藏的 skills
  reviews my                         我的审核提交
  notifications list [--unread]       通知列表
  notifications read <id>            标记已读
```

### 2.3 --namespace 行为变更

```typescript
// 旧行为:
"--namespace <ns>", "Namespace", "global"

// 新行为:
// 不指定 --namespace 时，搜索所有 accessible namespaces
// 指定 --namespace 时，仅在指定空间操作
```

### 2.4 install 命令重构

```typescript
// install 的 source 参数自动检测类型:
function parseSource(source: string): ParsedSource {
  // owner/repo[@ref][@skill] → git clone
  if (/^[\w-]+\/[\w-]+/.test(source)) {
    return { type: 'git', url: `https://github.com/${source}` };
  }
  // GitHub URL → git clone
  if (source.startsWith('https://github.com/')) {
    return { type: 'git', url: source };
  }
  // skill slug (namespace--name 或 name) → registry
  return { type: 'registry', slug: source };
}
```

### 2.5 交互式安装流程 (参考 vercel-labs/skills)

```
1. 解析 source
2. discoverSkills
3. 如果是交互模式:
   a. 显示 discovered skills 列表
   b. multiselect 选择要安装的 skills
   c. 显示检测到的 agents
   d. searchMultiselect 选择 target agents
   e. 选择 scope (project/global)
   f. 选择安装方式 (symlink/copy)
   g. 显示摘要，确认后执行
4. 如果是 --yes 模式，静默安装
```

### 2.6 update 命令完整实现

```typescript
// 读取 skillhub.lock
// 对每个 skill:
//   1. 调用 resolve API 获取最新版本
//   2. 比较版本
//   3. 如果有更新，调用 install 重新安装
// 支持 --dry-run
```

---

## 三、实现计划

### Phase 1: 核心重构 (高优先级)

1. **合并 uninstall/remove** → `uninstall.ts`
   - 删除 `remove.ts`
   - 更新 `cli.ts` 移除 `registerRemove`

2. **合并 add/install** → `install.ts`
   - 重构 `install.ts` 支持多种 source 类型
   - 添加 source parser
   - `add.ts` 改为简单的 alias 包装

3. **合并 info/inspect** → `inspect.ts`
   - 扩展 `inspect.ts` 支持单 namespace 模式
   - `info.ts` 改为 alias

4. **修改 namespace 默认行为**
   - 更新所有命令的 `--namespace` 选项
   - 核心函数支持 "all namespaces" 模式

### Phase 2: 交互式增强 (中优先级)

5. **实现交互式 find**
   - 复制 vercel-labs/skills 的 fzf 风格实现
   - 或使用 @clack/prompts 重写

6. **实现交互式 install**
   - 添加 skill multiselect
   - 添加 agent searchMultiselect
   - 添加摘要确认流程

7. **实现交互式 uninstall**
   - 添加 multiselect 选择要卸载的 skills

### Phase 3: 功能完善 (中优先级)

8. **实现 skillhub.lock**
   - 创建 `skill-lock.ts`
   - 修改 install 命令写入 lock
   - 修改 uninstall 命令删除 lock

9. **完善 update 命令**
   - 实现版本比较逻辑
   - 实现重新安装逻辑
   - 添加 --dry-run 支持

10. **添加 --dry-run 支持**
    - publish 命令
    - sync 命令

---

## 四、关键文件变更

```
删除:
  src/commands/remove.ts (合并到 uninstall)
  src/commands/info.ts (合并到 inspect)
  src/commands/add.ts (合并到 install)

修改:
  src/cli.ts                          更新命令注册
  src/commands/install.ts              合并 add 功能
  src/commands/uninstall.ts           合并 remove 功能
  src/commands/inspect.ts             合并 info 功能
  src/commands/delete.ts              添加 unpublish alias
  src/core/source-parser.ts           扩展 source 解析

新增:
  src/commands/find.ts                交互式搜索
  src/core/skill-lock.ts              锁文件管理
  src/core/interactive.ts             交互式选择组件
```

---

## 五、测试策略

### TDD 流程
1. 先写测试用例
2. 运行测试确认失败
3. 实现功能
4. 重构优化

### 关键测试场景
- install: git source 安装
- install: registry source 安装
- install: 交互式多选
- uninstall: 单个 skill 卸载
- uninstall: --all 全部卸载
- uninstall: --agent 过滤
- namespace: 默认搜索所有
- namespace: 指定单一空间
- update: --dry-run
- update: 实际更新
