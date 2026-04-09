# skillhub-cli 重构方案 v2

## 现状

### 命令问题
| 问题 | 当前状态 |
|-----|---------|
| remove vs uninstall 重复 | 两者都是删除本地已安装技能，代码几乎相同 |
| add vs install 模糊 | add 从 git 安装，install 从 registry 安装，但都调用相同的 discoverSkills + installSkill |
| info vs inspect 重复 | inspect 功能是 info 的超集（搜索所有 namespaces） |
| update 是 stub | 只打印 "Would update"，没有实际实现 |
| 无锁文件 | 缺少 skillhub.lock 来跟踪已安装的 skills |
| --namespace 默认值混乱 | 大多数命令默认 "global"，不符合直觉 |

### vercel-labs/skills 优秀特性

#### 1. 交互式 find (搜索)
```
fzf 风格:
- Raw readline + raw mode 实现终端选择
- 实时 debounced 搜索 (150-350ms 自适应)
- 上下箭头导航，回车选择，ESC 取消
- 显示安装数量 (格式: 1.2K, 3.5M)
- 非交互模式直接输出搜索结果

核心代码模式:
- process.stdin.setRawMode(true)
- readline.emitKeypressEvents(process.stdin)
- 自定义 render 函数重绘界面
```

#### 2. 交互式 add (多选)
```
searchMultiselect 组件:
- 通用 fuzzy 搜索多选组件
- 支持 locked section (universal agents 永远选中)
- 支持 hint 显示 (显示 agent 的 skillsDir)
- 保存 lastSelectedAgents 到 lock 文件
- Space 切换选择，上下导航，回车确认

安装流程:
1. 解析 source
2. discoverSkills
3. Skill multiselect 选择要安装的 skills
4. Agent searchMultiselect 选择 target agents
5. Scope 选择 (project/global)
6. 安装方式选择 (symlink/copy)
7. 显示摘要，确认后执行
```

#### 3. skill-lock.json 结构
```json
{
  "version": 3,
  "skills": {
    "skill-name": {
      "source": "owner/repo",
      "sourceType": "github|gitlab|registry|local",
      "sourceUrl": "完整 URL",
      "ref": "branch/tag",
      "skillPath": "skills/my-skill",
      "skillFolderHash": "GitHub tree SHA",
      "namespace": "global",
      "slug": "skill-name",
      "installedAt": "ISO timestamp",
      "updatedAt": "ISO timestamp"
    }
  },
  "dismissed": {},
  "lastSelectedAgents": ["claude-code"]
}
```

#### 4. check/update 机制
```
check 逻辑:
1. 读取 skill-lock.json
2. 对每个 skill，获取 skillFolderHash
3. 调用 GitHub Trees API 获取最新的 tree SHA
4. 比较 hash，不一致说明有更新
5. 显示可更新的 skills 列表

update 逻辑:
1. 调用 check 获取需要更新的 skills
2. 对每个需要更新的 skill，使用 spawnSync 重新执行 install
3. 更新 lock 文件中的 hash 和 updatedAt
```

---

## 重构方案

### 1. 命令合并

| 原命令 | 新命令 | 别名 | 说明 |
|--------|--------|------|------|
| remove, uninstall | uninstall | remove | 合并为一个 |
| add | install | add | 合并，source 自动检测类型 |
| info | inspect | info | 合并 |
| delete | delete | unpublish | 添加 alias |

### 2. 新命令体系

```
认证:
  login [--token] [--registry]         登录
  logout                                 登出
  whoami                                 显示当前用户

发现:
  search <query...> [-n/--limit]       搜索 skills
  explore [-n/--limit] [--sort newest|downloads|rating]  浏览最新 (重构为交互式)
  inspect <slug> [--namespace]           查看 skill 详情 (超集)
  info <slug>                            inspect 的 alias

安装/卸载:
  install <source> [-a] [-g] [--copy] [--list] [--dry-run]
    Source 自动检测:
    - owner/repo[@ref][@skill] → git clone
    - GitHub URL → git clone
    - namespace--slug 或 slug → registry
  add <source>                          install 的 alias

  uninstall <name> [--all] [-g] [--agent <agents...>] [--yes]
    --all 卸载所有
    --agent 过滤 agent
  remove <name>                         uninstall 的 alias

发布:
  publish [path] [--namespace] [--version] [--tags] [--dry-run]
  sync [path] [--namespace] [--dry-run]

版本管理:
  versions <slug> [--namespace]         列出版本
  update [slug] [--all] [--dry-run]     更新 skill (完整实现)
  check                                 检查更新 (新增)
  resolve <slug> [--version] [--tag]    解析版本

社交:
  star <slug> [--namespace] [--unstar]  收藏
  rate <slug> <score>                   评分 (1-5)
  rating <slug>                         查看评分

治理:
  archive <slug> [--namespace] [--yes] 归档
  hide <slug> [--namespace] [--yes]     隐藏 (admin)
  unhide <slug> [--namespace] [--yes]   取消隐藏 (admin)
  transfer <namespace> <userId> [--yes] 转让所有权
  delete <slug>, unpublish <slug>       删除 published skill

命名空间:
  namespaces                             列出可访问的命名空间
  init [name]                           创建 SKILL.md 模板

其他:
  list, ls [-g] [--json]                列出已安装 skills
  download <slug> [--namespace] [--version] [--tag] [--output]
  report <slug> [--namespace] [--reason] 举报
  me skills                             我发布的 skills
  me stars                              我收藏的 skills
  reviews my                            我的审核提交
  notifications list [--unread]         通知列表
  notifications read <id>                标记已读
```

### 3. --namespace 行为变更

```typescript
// 旧行为
"--namespace <ns>", "Namespace", "global"

// 新行为
// --namespace 不指定时，搜索所有 accessible namespaces
// --namespace 指定时，仅在指定空间操作
```

### 4. skillhub.lock 结构

```typescript
interface SkillLockEntry {
  source: string;           // e.g., "owner/repo" or "global/my-skill"
  sourceType: string;       // "git" | "registry" | "local"
  sourceUrl: string;        // 完整 URL
  ref?: string;            // branch/tag
  skillPath?: string;      // 子路径
  namespace: string;        // 命名空间
  slug: string;            // skill slug
  version: string;         // 当前安装的版本
  fingerprint?: string;    // 内容 hash
  installedAt: string;
  updatedAt: string;
}

interface SkillLockFile {
  version: number;         // 当前为 1
  skills: Record<string, SkillLockEntry>;
  lastSelectedAgents?: string[];
}
```

### 5. explore 重构 (交互式搜索)

参考 skills find.ts 实现:

```typescript
// 新 explore.ts
export function registerExplore(program: Command) {
  program
    .command("explore")
    .description("Browse latest or popular skills")
    .option("-n, --limit <n>", "Max results", "20")
    .option("--sort newest|downloads|rating", "Sort by", "newest")
    .option("--json", "JSON output")
    .action(async (opts) => {
      // 交互模式 (stdin.isTTY):
      //   - 显示搜索 prompt
      //   - 实时 debounced 搜索 (200ms)
      //   - 上下箭头选择
      //   - 回车安装选中的 skill
      
      // 非交互模式:
      //   - 直接显示结果列表
    });
}
```

### 6. install 重构 (交互式安装)

```typescript
// 新 install.ts 流程
export async function runInstall(source: string, options: InstallOptions) {
  // 1. 解析 source 类型
  const sourceInfo = parseSource(source);
  
  // 2. 获取 skills 列表
  const skills = await discoverSkills(sourceInfo);
  
  // 3. 交互式选择 (非 --yes 模式)
  if (!options.yes && process.stdin.isTTY) {
    // 3a. Skill multiselect
    const selectedSkills = await multiselectSkills(skills);
    
    // 3b. Agent searchMultiselect (locked: universal agents)
    const targetAgents = await selectAgents();
    
    // 3c. Scope 选择 (project/global)
    const scope = await selectScope();
    
    // 3d. 安装方式 (symlink/copy)
    const mode = await selectMode();
    
    // 3e. 显示摘要
    showInstallSummary(selectedSkills, targetAgents, scope, mode);
    
    // 3f. 确认
    if (!await confirm()) return;
  }
  
  // 4. 执行安装
  for (const skill of selectedSkills) {
    for (const agent of targetAgents) {
      await installSkill(skill, agent, scope, mode);
    }
  }
  
  // 5. 更新 skillhub.lock
  await updateLock(selectedSkills, sourceInfo);
}
```

### 7. check/update 完整实现

```typescript
// check 命令
export async function checkUpdates() {
  const lock = readSkillhubLock();
  const updates = [];
  
  for (const [name, entry] of Object.entries(lock.skills)) {
    if (entry.sourceType === 'git') {
      const latestHash = await fetchGitHash(entry);
      if (latestHash !== entry.fingerprint) {
        updates.push({ name, current: entry.version, latest: latestHash });
      }
    } else if (entry.sourceType === 'registry') {
      const resolve = await resolveVersion(entry.slug, entry.namespace);
      if (resolve.version !== entry.version) {
        updates.push({ name, current: entry.version, latest: resolve.version });
      }
    }
  }
  
  if (updates.length === 0) {
    console.log("All skills are up to date");
  } else {
    console.log(`${updates.length} update(s) available:`);
    for (const u of updates) {
      console.log(`  ${u.name}: ${u.current} → ${u.latest}`);
    }
  }
}

// update 命令
export async function updateSkills(slugs?: string[], dryRun?: boolean) {
  const lock = readSkillhubLock();
  const toUpdate = slugs || Object.keys(lock.skills);
  
  for (const slug of toUpdate) {
    const entry = lock.skills[slug];
    if (!entry) continue;
    
    if (dryRun) {
      console.log(`Would update ${slug}`);
    } else {
      // 重新安装
      await runInstall(entry.source, { yes: true });
      // 更新 lock
      lock.skills[slug].updatedAt = new Date().toISOString();
    }
  }
  
  writeSkillhubLock(lock);
}
```

---

## 实现计划

### Phase 1: 核心合并 (高优先级)

#### P1.1: 合并 uninstall/remove
- 删除 `remove.ts`
- 扩展 `uninstall.ts` 支持 `--all` 和 `--agent`
- 更新 cli.ts 移除 registerRemove，保留 registerUninstall，添加 remove alias

#### P1.2: 合并 add/install
- 扩展 `install.ts` 支持 source 类型自动检测
- `add.ts` 改为简单调用 install
- 更新 cli.ts

#### P1.3: 合并 info/inspect
- 扩展 `inspect.ts` 支持单 namespace 模式
- `info.ts` 改为 alias

#### P1.4: delete 添加 unpublish alias
- 更新 cli.ts

### Phase 2: 锁文件支持 (高优先级)

#### P2.1: skillhub.lock 核心
- 新建 `src/core/skill-lock.ts`
- 实现 read/write/updateLock/removeFromLock
- 实现 getSkillLockPath (支持 XDG_STATE_HOME)

#### P2.2: install 集成锁文件
- 安装后写入 lock
- 记录 source、version、fingerprint

#### P2.3: uninstall 集成锁文件
- 卸载后从 lock 移除
- 或标记为 uninstalled

### Phase 3: 交互式增强 (中优先级)

#### P3.1: searchMultiselect 组件
- 新建 `src/utils/search-multiselect.ts`
- 实现 fuzzy 搜索多选
- 支持 locked section
- 参考 skills prompts/search-multiselect.ts

#### P3.2: explore 重构 (交互式)
- 添加 --sort 选项 (newest/downloads/rating)
- 非交互模式保持现有行为
- 交互模式: 实时搜索 + 选中安装

#### P3.3: install 交互式安装
- Skill multiselect
- Agent searchMultiselect (locked universal)
- Scope 选择
- Mode 选择
- 摘要确认

### Phase 4: check/update 完整实现 (中优先级)

#### P4.1: check 命令
- 新建 `src/commands/check.ts`
- 读取 lock 文件
- 比较本地 hash vs 远程 hash
- 显示可更新的 skills

#### P4.2: update 完整实现
- 基于 check 结果
- 重新安装 outdated skills
- 更新 lock 文件

---

## 文件变更

```
删除:
  src/commands/remove.ts

修改:
  src/cli.ts                    更新命令注册
  src/commands/install.ts       合并 add，添加交互
  src/commands/uninstall.ts      合并 remove，添加 --all/--agent
  src/commands/inspect.ts        合并 info
  src/commands/explore.ts       添加交互式
  src/commands/update.ts        完整实现
  src/commands/delete.ts        添加 unpublish alias

新增:
  src/commands/check.ts         检查更新命令
  src/core/skill-lock.ts        锁文件管理
  src/utils/search-multiselect.ts 交互式多选组件
  src/utils/prompts.ts          CLI prompts 工具
```

---

## 依赖变更

```json
// package.json 新增依赖
{
  "@clack/prompts": "^0.7.0",  // 替代 ora 的更美观的 prompt
  "picocolors": "^1.0.0"         // 跨平台颜色
}

// 可选: 如果要复用 skills 的 search-multiselect，可能需要调整
```

---

## 测试策略

### 核心测试场景

```typescript
// install.test.ts
describe("install", () => {
  it("parses git source: owner/repo");
  it("parses git source: GitHub URL");
  it("parses registry source: slug");
  it("parses registry source: namespace--slug");
  it("discovers skills from cloned repo");
  it("writes to skillhub.lock after install");
});

// uninstall.test.ts
describe("uninstall", () => {
  it("removes skill from disk");
  it("removes skill from lock file");
  it("supports --all flag");
  it("supports --agent filter");
});

// check.test.ts
describe("check", () => {
  it("detects outdated git skills");
  it("detects outdated registry skills");
  it("shows no updates when all current");
});

// skill-lock.test.ts
describe("skill-lock", () => {
  it("reads lock file");
  it("writes lock file");
  it("updates existing entry");
  it("removes entry");
});
```

---

## 优先级排序

| 优先级 | 任务 | 原因 |
|--------|------|------|
| P0 | 合并 remove/uninstall | 消除重复 |
| P0 | 合并 add/install | 统一安装体验 |
| P0 | skillhub.lock 基础 | 后续功能依赖 |
| P1 | install 集成 lock | 核心功能 |
| P1 | uninstall 集成 lock | 核心功能 |
| P1 | check 命令 | update 前置 |
| P1 | update 完整实现 | 核心功能 |
| P2 | searchMultiselect 组件 | 交互式基础 |
| P2 | explore 交互式 | UX 提升 |
| P2 | install 交互式安装 | UX 提升 |
