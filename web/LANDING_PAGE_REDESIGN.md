# SkillHub 落地页重新设计

## 设计理念

采用"技术编织"（Tech Weave）美学风格，通过以下元素传达 SkillHub 作为企业级技能注册中心的专业性和创新性：

### 视觉特点

1. **动态粒子系统**
   - Canvas 实现的粒子连接动画
   - 象征技能之间的连接和协作
   - 80个粒子节点，动态连线效果

2. **配色方案**
   - 深蓝紫色基调（slate-950, indigo-950）
   - 霓虹青色点缀（cyan-500）
   - 紫罗兰色辅助（violet-500）
   - 营造科技感和未来感

3. **字体选择**
   - 标题：Syne（几何感强，现代）
   - 正文：IBM Plex Sans（技术感，易读）
   - 代码：JetBrains Mono

4. **动效设计**
   - 渐入动画（fade-up, fade-in）
   - 悬浮卡片效果（hover:scale-105）
   - 渐变光晕（gradient glow）
   - 脉冲动画（pulse）

### 内容结构

1. **Hero 区域**
   - 大标题 + 渐变文字效果
   - 搜索栏（带光晕效果）
   - CTA 按钮（探索技能 / 发布技能）
   - 统计数据展示（技能包、下载量、团队）

2. **特性展示**
   - 6个核心特性卡片
   - 图标 + 标题 + 描述
   - 悬浮交互效果

3. **CTA 区域**
   - 快速开始指引
   - 命令行示例
   - 行动按钮

4. **页脚**
   - 版权信息
   - 导航链接

## 技术实现

- React + TypeScript
- TailwindCSS
- Canvas API（粒子动画）
- TanStack Router

## 文件变更

- 新增：`web/src/pages/landing.tsx` - 新的落地页组件
- 修改：`web/src/app/router.tsx` - 路由配置，将 landing 设为首页
- 修改：`web/index.html` - 更新字体引用
- 修改：`web/src/index.css` - 更新字体配置

## 本地预览

```bash
cd web
npm install --legacy-peer-deps
npm run dev
```

访问 `http://localhost:5173` 查看新的落地页。
