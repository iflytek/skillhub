# Do / Don't（正例 · 反例 · 为什么）

> 每条都给出可复制的最小片段，片段只使用库 token（`--sh-*`）。反例不是“风格差异”，而是会在换主题、长文案或窄屏下直接坏掉的做法。

## 1. 只用 token，不写死颜色

**为什么**：写死色值后，切暗色主题不会跟着变；对比度也无法被统一重算。

```css
/* Do */
.card { background: var(--sh-bg-raised); border: 1px solid var(--sh-border-default); }
```

```css
/* Don't */
.card { background: #ffffff; border: 1px solid #e5e5e5; }
```

## 2. 主色只给主操作

**为什么**：一个视图多个主按钮，用户无法判断主路径；暗色下还会形成高亮块竞争。

```tsx
// Do
<Button variant="primary">安装技能</Button>
<Button variant="secondary">查看文件</Button>
```

```tsx
// Don't
<Button variant="primary">安装技能</Button>
<Button variant="primary">收藏</Button>
<Button variant="primary">下载</Button>
```

## 3. 结构用发丝线，层级用留白

**为什么**：每张卡片都叠重阴影会让层级噪声化，暗色下尤其糊成一片。

```css
/* Do */
.section + .section { border-top: 1px solid var(--sh-border-subtle); }
```

```css
/* Don't */
.card { box-shadow: 0 16px 32px rgb(0 0 0 / 0.24); }
```

## 4. 圆角只用刻度值

**为什么**：同屏出现 6/8/12/16/999 五种圆角，边界节奏会失控；暗色下圆角与描边一起看更明显。

```css
/* Do */
.button { border-radius: var(--sh-radius-sm); }   /* 8px */
.panel  { border-radius: var(--sh-radius-md); }   /* 10px */
.badge  { border-radius: var(--sh-radius-full); }
```

```css
/* Don't */
.a { border-radius: 6px; } .b { border-radius: 12px; } .c { border-radius: 16px; }
```

## 5. 图标只用 lucide，一线到底

**为什么**：线性与面性混排会让同一行的图标重量不一致，视觉上像两套产品。

```tsx
// Do
import { Download, Star, FileText } from 'lucide-react'
```

```tsx
// Don't
<svg viewBox="0 0 24 24"><path d="..." /></svg>  {/* 手写简化 SVG 替代 lucide */}
```

## 6. 中文直给，不用营销词

**为什么**：破坏性操作与错误提示需要可执行信息，营销词会稀释后果。

```text
// Do
删除后不可恢复，确认删除「@global/my-skill」？

// Don't
开启全新智能体验，一键焕新你的技能库！
```

## 7. 破坏性操作必须二次确认

**为什么**：误触的直接后果是版本不可恢复；确认框要写清对象与后果。

```tsx
// Do
<ConfirmDialog title="下架 v1.4.0？" description="已安装的用户将无法再下载该版本。" confirmText="下架" tone="danger" />
```

```tsx
// Don't
<Button onClick={yank}>下架</Button>  {/* 无确认、无后果说明 */}
```

## 8. 状态不能只靠颜色

**为什么**：色盲用户无法区分红/绿；状态色也必须能在暗色下保持 4.5:1。

```tsx
// Do
<Badge tone="success">已发布</Badge>
```

```tsx
// Don't
<span style={{ width: 8, height: 8, borderRadius: 999, background: 'var(--sh-success-base)' }} />
```

## 9. 表格数字右对齐并用等宽数字

**为什么**：比例字宽会让小数点参差，扫读时无法按位比较。

```css
/* Do */
.num { text-align: right; font-variant-numeric: tabular-nums; }
```

```css
/* Don't */
.num { text-align: left; }
```

## 10. label 在控件上方，错误紧贴控件

**为什么**：用 placeholder 当 label，输入后标签消失；错误放页面顶部会让用户找不到对应字段。

```tsx
// Do
<Field label="命名空间" error="只能使用小写字母、数字与连字符">
  <Input />
</Field>
```

```tsx
// Don't
<Input placeholder="命名空间" />
<div className="page-error">命名空间格式错误</div>
```

## 11. 暗色必须逐屏验证

**为什么**：只改背景不改软底/描边，会在暗色下出现亮色徽标底、看不见的分隔线。

```css
/* Do */
.dark .badge-success { background: var(--sh-success-weak); color: var(--sh-success-on-weak); }
```

```css
/* Don't */
.dark body { background: var(--sh-bg-page); }  /* 其余仍是亮色值 */
```

## 12. 空态给下一步，不给插画

**为什么**：空态出现频率高，插画会拖慢首屏并增加多语言维护成本；用户需要的是“为什么空 + 怎么办”。

```tsx
// Do
<EmptyState title="没有匹配的技能" description="清除筛选可查看全部 128 个技能" action={<Button>清除全部筛选</Button>} />
```

```tsx
// Don't
<EmptyState illustration={<HeroImage />} />
```

## 13. 焦点只许一种表达，禁止 box-shadow

**为什么**：同一控件上「边框转色 + 外环」会形成双层圈；`box-shadow: 0 0 0 Npx` 造的环会随圆角、裁切、层叠变形。文本录入控件在鼠标点击时也会命中 `:focus-visible`，因此不能靠 `:focus-visible` 区分指针与键盘。

```css
/* Do：字段只转边框 */
.input:focus { outline: none; border-color: var(--sh-border-focus); }

/* Do：非文本控件只在键盘态出环 */
.btn:focus-visible { outline: 2px solid var(--sh-border-focus); outline-offset: 2px; }
```

```css
/* Don't：边框 + 外环双层 */
.input:focus-visible {
  border-color: var(--sh-border-focus);
  outline: 2px solid var(--sh-border-focus);
  outline-offset: 2px;
}

/* Don't：用阴影假装焦点环 */
.input:focus { box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.3); }
```

## 14. 卡片嵌套不超过两层

**为什么**：边框叠加会让层级失去意义，暗色下更明显。

```tsx
// Do
<Panel><Card>内容</Card></Panel>
```

```tsx
// Don't
<Card><Card><Card>内容</Card></Card></Card>
```

## 15. 中文与拉丁字母之间留空格

**为什么**：混排留空格是中文排版的基本可读性规则，也影响截断时的断点位置。

```text
// Do
使用 SKILL.md 文件，支持 3 种模式

// Don't
使用SKILL.md文件，支持3种模式
```

## 16. 关键信息不截断

**为什么**：列表里截掉 `@namespace` 会让同名技能无法区分；详情页必须给出完整坐标。

```css
/* Do */
.list-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-title { white-space: normal; overflow-wrap: anywhere; }
```

```css
/* Don't */
.list-title { width: 120px; }  /* 直接把 @namespace 裁掉，且无 tooltip */
```

## 17. 可截断文本的父级必须 `min-width: 0`

**为什么**：`flex` / `grid` 子项默认 `min-width: auto`，省略号永远不会出现，长文案会把布局撑破。

```css
/* Do */
.row { display: grid; grid-template-columns: 32px minmax(0, 1fr) 96px; }
.title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
```

```css
/* Don't */
.row { display: grid; grid-template-columns: 32px 1fr 96px; }
```

## 18. 俄语长词允许任意断行

**为什么**：俄语复合词可达 40+ 字符，不设断行会横向溢出；按音节断字又会在窄列里产生错误断点。

```css
/* Do */
[lang="ru"] .description { overflow-wrap: anywhere; hyphens: auto; }
```

```css
/* Don't */
.description { white-space: nowrap; }
```
