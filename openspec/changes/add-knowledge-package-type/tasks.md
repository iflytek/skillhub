## 1. Current spec-only deliverables

- [x] 1.1 依据冻结源码核对身份、版本/指针、解析回退、历史扩展元数据与响应消费者。
- [x] 1.2 编写 proposal、design 和候选 scenarios；全部策略标为 Proposed，不提交运行实现。
- [x] 1.3 运行 OpenSpec 1.14.3 strict validate 与 status，并检查 diff、引用与本地链接。
- [ ] 1.4 由独立审阅者复核候选实际文件、维护者请求覆盖及未决 gate；修订后重验。

## 2. Maintainer agreement gates

- [ ] 2.1 G1：确认键名、规范值、新提交拒绝规则、启用边界及历史保守分类/可信适用性方案。
- [ ] 2.2 G2：确认逐版本类型、缺省不继承、公共/预览/固定版本/yank/Promotion 的生命周期建议。
- [ ] 2.3 G3：确认显式响应 opt-in、首批端点、过滤与错误规则、缓存隔离及严格旧消费者验收。
- [ ] 2.4 在上述共识后单独批准实现范围；spec 合入不自动授权实现或发行。

## 3. Future implementation — blocked pending agreement

以下仅为后续实施的建议，不属于本次交付，不标记为已完成。

- [ ] 3.1 实现新声明校验及可信逐版本分类；防止松散回退和历史键碰撞改变类型，不改写发布包。
- [ ] 3.2 在既有投影/解析后附加分类，覆盖混合版本、较低 semver 后发布、预览与无 survivor 的 yank。
- [ ] 3.3 实现 opt-in 响应与资源过滤，维护分页/计数/权限语义、异步索引校验与表示缓存隔离。
- [ ] 3.4 回归同 owner / 跨 owner 碰撞、Skill/Suite 同 slug、Suite 精确成员与所选源版本 Promotion。
- [ ] 3.5 使用未修改 CLI / 兼容客户端及严格未知字段解码器，实测搜索、resolve、下载和标准 Skill 安装。
- [ ] 3.6 验证原扫描/审核/授权/治理拒绝路径；按实际 API/schema 改动运行对应生成类型及消费者检查。
- [ ] 3.7 另行评估迁移、回滚、发布与用户文档，不以本稿结构校验替代产品验收。
