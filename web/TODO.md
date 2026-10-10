# SkillHub 前端待办事项

## 高优先级

### 1. i18n 语言切换 Bug
- **问题**: 切换到 English 没有反应
- **位置**: `src/shared/components/language-switcher.tsx`
- **需要检查**:
  - i18next 配置是否正确
  - 语言切换事件是否正确触发
  - 翻译文件是否正确加载

### 2. 完善 i18n 支持
- **需要添加翻译的组件**:
  - Toast 通知消息
  - ConfirmDialog 对话框
  - 发布页面的审核提示
  - 审核详情页面
  - Token 管理页面
  - 所有新增的 UI 文本

### 3. 统一异常处理
- **需求**: 为所有 API 调用添加统一的错误处理
- **实现方案**:
  - 创建 API 拦截器
  - 自动显示错误 toast
  - 处理 401/403 等特殊状态码
  - 统一错误消息格式

### 4. 完善 Toast 通知
- **需要替换的地方**:
  - 所有剩余的 `alert()` 调用
  - 所有剩余的 `window.confirm()` 调用
  - 添加加载状态的 toast
  - 添加 promise toast 用于异步操作

## 中优先级

### 5. 审核详情页对话框
- **位置**: `src/pages/dashboard/review-detail.tsx`
- **需要**: 完成 ConfirmDialog 的集成
- **状态**: 部分完成，需要添加对话框到 JSX

### 6. 错误边界
- **需求**: 添加 React Error Boundary
- **功能**: 捕获组件错误并显示友好提示

### 7. 加载状态优化
- **需求**: 统一加载状态的显示
- **实现**: 创建全局加载组件

## 低优先级

### 8. 性能优化
- 代码分割
- 懒加载
- 图片优化

### 9. 可访问性
- ARIA 标签
- 键盘导航
- 屏幕阅读器支持

## 已完成

- ✅ 创建 Toast 通知系统
- ✅ 创建 ConfirmDialog 组件
- ✅ 发布页面添加审核提示
- ✅ Token 列表使用 SPA 对话框
- ✅ 发布页面使用 Toast 通知
- ✅ 为用户添加 SKILL_ADMIN 角色
