# 可选统计接入

旧的部署方脚本注入示例已迁入产品模块，不再作为部署入口。
请使用[配置接入指南](../../frontend-analytics-integration.md)。
唯一源码位于 `web/src/features/analytics/`。PR-1 的历史 edge 配置仅作为测试 fixture 保留，
不应部署。无需复制 JS，也不需要额外 Nginx。
