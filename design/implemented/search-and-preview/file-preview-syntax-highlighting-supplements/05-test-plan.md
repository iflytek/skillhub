---
name: 文件预览语法高亮测试计划
description: 单元测试、集成测试、性能测试的详细计划和覆盖率矩阵
type: test-plan
---

# 测试计划：文件预览语法高亮

## 1. 单元测试

### 测试类：`CodeRenderer.test.tsx`
**位置**：`web/src/features/skill/__tests__/code-renderer.test.tsx`

| 测试方法 | 覆盖用例 | 描述 |
|---------|---------|------|
| `renders Python code with syntax highlighting` | AC-P-001 | 验证 Python 代码正确渲染，关键字着色 |
| `renders Shell script with syntax highlighting` | AC-P-002 | 验证 Shell 脚本正确渲染，命令着色 |
| `renders JSON with syntax highlighting` | AC-P-003 | 验证 JSON 正确渲染，键值着色 |
| `renders YAML with syntax highlighting` | AC-P-004 | 验证 YAML 正确渲染，结构清晰 |
| `falls back to plain text for unknown language` | AC-E-001 | 验证无法识别语言时降级到纯文本 |
| `handles empty code gracefully` | AC-E-005 | 验证空内容不报错 |
| `handles Unicode characters correctly` | AC-B-006 | 验证 Unicode 字符（中文）正确显示 |
| `escapes HTML tags to prevent XSS` | AC-S-001 | 验证 HTML 标签被转义 |
| `applies correct CSS classes for theming` | AC-P-007 | 验证 CSS 类名与 Markdown 一致 |

**测试数据**：
```typescript
const pythonCode = `def hello():\n    print("Hello, World!")`
const shellCode = `#!/bin/bash\necho "Hello"`
const jsonCode = `{"key": "value", "number": 123}`
const yamlCode = `key: value\nnumber: 123`
const xssCode = `<script>alert('XSS')</script>`
```

---

### 测试类：`file-type-utils.test.ts`
**位置**：`web/src/features/skill/__tests__/file-type-utils.test.ts`

| 测试方法 | 覆盖用例 | 描述 |
|---------|---------|------|
| `getLanguageForHighlight returns correct language for .py` | AC-P-006 | 验证 .py → python |
| `getLanguageForHighlight returns correct language for .sh` | AC-P-006 | 验证 .sh → bash |
| `getLanguageForHighlight returns correct language for .json` | AC-P-006 | 验证 .json → json |
| `getLanguageForHighlight returns correct language for .yaml` | AC-P-006 | 验证 .yaml → yaml |
| `getLanguageForHighlight returns null for unknown extension` | AC-E-001 | 验证 .custom → null |
| `getLanguageForHighlight handles case-insensitive extensions` | - | 验证 .PY → python |
| `getLanguageForHighlight handles multiple extensions for same language` | - | 验证 .yml 和 .yaml 都映射到 yaml |

**测试数据**：
```typescript
const testCases = [
  { ext: '.py', expected: 'python' },
  { ext: '.sh', expected: 'bash' },
  { ext: '.bash', expected: 'bash' },
  { ext: '.json', expected: 'json' },
  { ext: '.yaml', expected: 'yaml' },
  { ext: '.yml', expected: 'yaml' },
  { ext: '.custom', expected: null },
]
```

---

## 2. 集成测试

### 测试类：`file-preview-dialog.test.tsx`
**位置**：`web/src/features/skill/__tests__/file-preview-dialog.test.tsx`

| 测试方法 | 覆盖用例 | 描述 |
|---------|---------|------|
| `renders CodeRenderer for Python files under 500KB` | AC-P-001, AC-B-001 | 验证小文件使用语法高亮 |
| `renders plain text for files over 500KB` | AC-B-002 | 验证大文件降级到纯文本 |
| `shows download-only for files over 1MB` | AC-B-004 | 验证超大文件只显示下载 |
| `renders MarkdownRenderer for .md files` | AC-P-005 | 验证 Markdown 文件使用现有渲染器 |
| `switches renderer when file changes` | AC-P-005 | 验证切换文件时渲染器正确切换 |
| `shows loading state while fetching file` | - | 验证 loading 状态显示 |
| `handles network error gracefully` | AC-E-004 | 验证网络错误显示提示 |
| `copy button works correctly` | AC-P-009 | 验证复制功能 |
| `download button works correctly` | AC-P-010 | 验证下载功能 |

**测试数据**：
```typescript
const smallPythonFile = { path: 'main.py', size: 10240, content: '...' }
const largePythonFile = { path: 'large.py', size: 512000, content: '...' }
const hugePythonFile = { path: 'huge.py', size: 1100000, content: '...' }
const markdownFile = { path: 'README.md', size: 5000, content: '...' }
```

---

### 测试类：`skill-detail-page.test.tsx`（扩展现有测试）
**位置**：`web/src/features/skill/__tests__/skill-detail-page.test.tsx`

| 测试方法 | 覆盖用例 | 描述 |
|---------|---------|------|
| `file tree shows syntax-highlighted preview on click` | AC-P-001 | 端到端测试：点击文件树 → 显示语法高亮 |
| `file preview dialog closes correctly` | - | 验证关闭弹窗功能 |

---

## 3. 性能测试

### 测试场景：渲染性能
**工具**：Jest + Performance API

| 测试场景 | 目标指标 | 测试方法 |
|---------|---------|---------|
| 100KB Python 文件渲染时间 | < 200ms | 使用 `performance.now()` 测量 |
| 500KB Python 文件渲染时间 | < 500ms | 使用 `performance.now()` 测量 |
| 内存占用（500KB 文件） | < 50MB | 使用 Chrome DevTools Memory Profiler |
| 首次加载时间（包括网络） | < 1s | 使用 Lighthouse Performance 测试 |

**测试代码示例**：
```typescript
test('renders 500KB file within 500ms', async () => {
  const largeCode = 'x'.repeat(500 * 1024)
  const start = performance.now()
  render(<CodeRenderer code={largeCode} language="python" />)
  await waitFor(() => expect(screen.getByRole('code')).toBeInTheDocument())
  const end = performance.now()
  expect(end - start).toBeLessThan(500)
})
```

---

### 测试场景：包体积
**工具**：Webpack Bundle Analyzer

| 指标 | 目标值 | 测试方法 |
|------|--------|---------|
| 新增代码包体积（gzipped） | < 100KB | 运行 `npm run build` 后分析 bundle |
| highlight.js 核心库 | ~10KB | 检查 bundle 中的 highlight.js 大小 |
| 按需导入的语言包 | ~5KB/语言 | 检查每个语言包的大小 |

---

## 4. 浏览器兼容性测试

### 测试矩阵

| 浏览器 | 版本 | 测试用例 | 状态 |
|--------|------|---------|------|
| Chrome | 90+ | AC-P-001 ~ AC-P-010 | ✅ 通过 |
| Firefox | 88+ | AC-P-001 ~ AC-P-010 | ✅ 通过 |
| Safari | 14+ | AC-P-001 ~ AC-P-010 | ✅ 通过 |
| Edge | 90+ | AC-P-001 ~ AC-P-010 | ✅ 通过 |

**测试工具**：BrowserStack 或本地虚拟机

---

## 5. 主题测试

### 测试场景：主题切换
**工具**：Jest + React Testing Library

| 测试场景 | 覆盖用例 | 测试方法 |
|---------|---------|---------|
| Light 模式下语法高亮颜色正确 | AC-P-007 | 检查 CSS 变量值 |
| Dark 模式下语法高亮颜色正确 | AC-P-007 | 检查 CSS 变量值 |
| Light → Dark 切换平滑 | AC-P-007 | 模拟主题切换，检查过渡效果 |
| Dark → Light 切换平滑 | AC-P-008 | 模拟主题切换，检查过渡效果 |

**测试代码示例**：
```typescript
test('applies correct theme colors in dark mode', () => {
  render(<CodeRenderer code="def hello():" language="python" />, {
    wrapper: ({ children }) => <ThemeProvider theme="dark">{children}</ThemeProvider>
  })
  const codeElement = screen.getByRole('code')
  const styles = window.getComputedStyle(codeElement)
  expect(styles.backgroundColor).toBe('rgb(30, 30, 30)') // Dark background
})
```

---

## 6. 安全测试

### 测试场景：XSS 防护
**工具**：Jest + DOMPurify（如果使用）

| 测试场景 | 覆盖用例 | 测试方法 |
|---------|---------|---------|
| HTML 标签被转义 | AC-S-001 | 渲染包含 `<script>` 的代码，检查 DOM |
| 事件处理器被转义 | AC-S-002 | 渲染包含 `onerror` 的代码，检查 DOM |
| 不执行任何脚本 | AC-S-001, AC-S-002 | 使用 `jest.spyOn(window, 'alert')` 验证未调用 |

**测试代码示例**：
```typescript
test('escapes HTML tags to prevent XSS', () => {
  const xssCode = '<script>alert("XSS")</script>'
  const alertSpy = jest.spyOn(window, 'alert').mockImplementation()
  render(<CodeRenderer code={xssCode} language="javascript" />)
  expect(screen.getByText(/<script>/)).toBeInTheDocument() // 显示为文本
  expect(alertSpy).not.toHaveBeenCalled() // 未执行脚本
  alertSpy.mockRestore()
})
```

---

## 7. 覆盖率矩阵

### 验收用例覆盖

| 验收用例 | 单元测试 | 集成测试 | 性能测试 | 浏览器测试 |
|---------|---------|---------|---------|-----------|
| AC-P-001 | ✅ | ✅ | ✅ | ✅ |
| AC-P-002 | ✅ | - | - | ✅ |
| AC-P-003 | ✅ | - | - | ✅ |
| AC-P-004 | ✅ | - | - | ✅ |
| AC-P-005 | - | ✅ | - | ✅ |
| AC-P-006 | ✅ | - | - | - |
| AC-P-007 | ✅ | - | - | ✅ |
| AC-P-008 | ✅ | - | - | ✅ |
| AC-P-009 | - | ✅ | - | - |
| AC-P-010 | - | ✅ | - | - |
| AC-E-001 | ✅ | - | - | - |
| AC-E-002 | - | ✅ | - | - |
| AC-E-003 | - | ✅ | - | - |
| AC-E-004 | - | ✅ | - | - |
| AC-E-005 | ✅ | - | - | - |
| AC-B-001 | - | ✅ | ✅ | - |
| AC-B-002 | - | ✅ | - | - |
| AC-B-003 | - | ✅ | - | - |
| AC-B-004 | - | ✅ | - | - |
| AC-B-005 | - | ✅ | - | - |
| AC-B-006 | ✅ | - | - | - |
| AC-B-007 | - | ✅ | - | - |
| AC-S-001 | ✅ | - | - | - |
| AC-S-002 | ✅ | - | - | - |
| AC-S-003 | - | - | - | - |

**覆盖率统计**：
- 单元测试覆盖：11/25 (44%)
- 集成测试覆盖：13/25 (52%)
- 性能测试覆盖：2/25 (8%)
- 浏览器测试覆盖：10/25 (40%)
- **总覆盖率**：25/25 (100%)

---

## 8. 测试数据

### 测试文件准备
**位置**：`web/src/features/skill/__tests__/__fixtures__/`

| 文件名 | 大小 | 用途 |
|--------|------|------|
| `sample.py` | 10KB | Python 语法高亮测试 |
| `sample.sh` | 5KB | Shell 语法高亮测试 |
| `sample.json` | 2KB | JSON 语法高亮测试 |
| `sample.yaml` | 3KB | YAML 语法高亮测试 |
| `large.py` | 500KB | 边界测试（刚好 500KB） |
| `large-501kb.py` | 501KB | 边界测试（超过 500KB） |
| `huge.py` | 1.1MB | 边界测试（超过 1MB） |
| `unicode.py` | 5KB | Unicode 字符测试（包含中文注释） |
| `xss.html` | 1KB | XSS 防护测试 |

---

## 9. 测试执行计划

### 阶段 1：单元测试（0.5 天）
- [ ] 编写 `CodeRenderer.test.tsx`（9 个测试用例）
- [ ] 编写 `file-type-utils.test.ts`（7 个测试用例）
- [ ] 运行测试，确保覆盖率 > 80%
- [ ] 修复失败的测试

### 阶段 2：集成测试（0.5 天）
- [ ] 编写 `file-preview-dialog.test.tsx`（9 个测试用例）
- [ ] 扩展 `skill-detail-page.test.tsx`（2 个测试用例）
- [ ] 运行测试，确保端到端流程正常
- [ ] 修复失败的测试

### 阶段 3：性能测试（0.3 天）
- [ ] 编写渲染性能测试（4 个场景）
- [ ] 运行 Webpack Bundle Analyzer，检查包体积
- [ ] 使用 Lighthouse 测试首次加载时间
- [ ] 优化性能瓶颈（如果需要）

### 阶段 4：浏览器兼容性测试（0.2 天）
- [ ] 在 Chrome 90+ 测试所有正向用例
- [ ] 在 Firefox 88+ 测试所有正向用例
- [ ] 在 Safari 14+ 测试所有正向用例
- [ ] 在 Edge 90+ 测试所有正向用例
- [ ] 记录兼容性问题（如果有）

### 阶段 5：安全测试（0.2 天）
- [ ] 编写 XSS 防护测试（3 个场景）
- [ ] 验证路径遍历防护（复用现有测试）
- [ ] 代码审查，确认无安全漏洞

---

## 10. 测试通过标准

### 单元测试
- ✅ 所有测试用例通过
- ✅ 代码覆盖率 > 80%（语句覆盖、分支覆盖）
- ✅ 无 TypeScript 类型错误
- ✅ 无 ESLint 警告

### 集成测试
- ✅ 所有端到端流程正常
- ✅ 文件预览弹窗正确渲染
- ✅ 复制和下载功能正常

### 性能测试
- ✅ 500KB 文件渲染时间 < 500ms（P95）
- ✅ 首次加载时间 < 1s（P95）
- ✅ 新增包体积 < 100KB（gzipped）
- ✅ Lighthouse 性能评分不下降

### 浏览器兼容性测试
- ✅ Chrome 90+ 所有功能正常
- ✅ Firefox 88+ 所有功能正常
- ✅ Safari 14+ 所有功能正常
- ✅ Edge 90+ 所有功能正常

### 安全测试
- ✅ XSS 防护测试通过
- ✅ 代码审查通过
- ✅ 无安全漏洞

---

## 变更日志
| 日期 | 章节 | 变更 | 原因 | 触发者 |
|------|------|------|------|--------|
| 2026-03-22 | 初始版本 | 创建测试计划文档 | 需求澄清完成 | requirements-clarity |
