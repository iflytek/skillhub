# 已实现需求文档归档

本目录用于收敛“已经完成实现”的 PRD 及其补充材料。

这些文档不再作为待办需求管理，而是作为：
- 实现后的设计归档
- 历史决策回溯材料
- 代码与实现范围的对照入口

## 归档目录

### 1. 本地开发与运行时
- [local-dev-runtime/local-h2-dev-mode-v1.0-prd.md](./local-dev-runtime/local-h2-dev-mode-v1.0-prd.md)
- [local-dev-runtime/local-h2-file-persistence-and-redis-strategy-v1.0-prd.md](./local-dev-runtime/local-h2-file-persistence-and-redis-strategy-v1.0-prd.md)

已实现依据：
- 仓库已存在 `local-h2` 配置与种子数据
- 已存在 H2 搜索降级实现
- 相关 Redis / 本地模式策略已进入代码路径

### 2. 搜索、预览与文件浏览
- [search-and-preview/file-preview-syntax-highlighting-v1.0-prd.md](./search-and-preview/file-preview-syntax-highlighting-v1.0-prd.md)
- [search-and-preview/file-preview-syntax-highlighting-supplements/README.md](./search-and-preview/file-preview-syntax-highlighting-supplements/README.md)
- [search-and-preview/skill-file-browser-sidebar-v1.0-prd.md](./search-and-preview/skill-file-browser-sidebar-v1.0-prd.md)

已实现依据：
- 前端已存在 `code-renderer.tsx` 与 `file-preview-dialog.tsx`
- 技能详情页与审核详情页已具备文件浏览/预览交互

### 3. 安全与审核
- [security-and-governance/security-audit-ui-v1.0-prd.md](./security-and-governance/security-audit-ui-v1.0-prd.md)

已实现依据：
- 前端已存在 `security-audit` feature
- 技能详情页与审核详情页均已接入安全审核展示

## 说明

- 这里的“已实现”表示主路径能力已经进入仓库代码，不要求与原 PRD 逐字逐项完全一致。
- 如果后续出现第二阶段扩展需求，建议重新回到 `docs/prds` 新开 PRD，而不是继续在这里追加未实现规划。

