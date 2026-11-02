# 前端动作统计

SkillHub 默认不采集前端动作、不加载统计 SDK。页面提供被动的语义标签，部署方可按自身授权和隐私策略选择接入。

当前已交付的是手动接入示例：部署方维护采集入口、适配器及代理配置。Docker／Compose 或 Helm 配置化开启、按需加载的能力仍在后续 PR 中推进，不能将其作为当前部署步骤。

- [中文接入指南](https://github.com/iflytek/skillhub/blob/main/docs/frontend-analytics-integration.md)：复用已有平台或在隔离环境运行 Umami 演示，配置规则、检查真实入库、分别验证关闭态和启用态故障，最后恢复或清理。
- [英文接口与隐私契约](https://github.com/iflytek/skillhub/blob/main/docs/frontend-analytics.md)：事件含义、适配器边界、传输字段及子路径映射。

点击下载只表示下载意图，不证明下载、安装或执行成功。不要把管理密钥放到浏览器；平台数据保留、访问控制和合规由部署方负责。指南对应仓库 main；固定版本部署应阅读相同 tag 的文档与示例。
