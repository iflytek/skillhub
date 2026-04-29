# Skill 搜索与发现

本页聚焦“如何找到并使用合适的技能包”。搜索接口细节和底层设计不在这里重复展开。

## 核心能力

- 全文搜索
- 按命名空间、标签和排序方式过滤
- 权限感知结果集
- Web 与 CLI 两种入口

## 常用方式

### Web UI

1. 访问 `/search`
2. 输入关键词
3. 按需要选择命名空间、标签和排序方式
4. 打开技能详情页查看版本、文件和安装方式

### CLI

```bash
export CLAWHUB_REGISTRY=http://localhost:8080

npx clawhub search pdf
npx clawhub install pdf-parser
npx clawhub install my-team--pdf-parser
```

## 搜索时建议关注的维度

- 技能名称与摘要是否准确描述用途
- 命名空间是否可信
- 最近更新时间是否足够新
- 下载量、评分、星标等是否符合预期
- 是否存在审核或扫描相关风险提示

## 命名空间与坐标

- 全局技能通常可直接按 slug 安装
- 命名空间下的技能通常使用 `<namespace>--<skill>` 形式
- CLI 和 Web 搜索结果可能因权限范围不同而不同

## 使用建议

- 新成员先浏览命名空间和热门技能，建立整体认知
- 先看详情页再安装，避免装到名称相似但用途不同的包
- 团队内部最好统一标签规范，提升过滤效果

## 继续阅读

- [Skill 发布与版本管理](/guide/skill-publish)
- [命名空间与团队管理](/guide/namespace)
- [用户交互与社交](/guide/social)
