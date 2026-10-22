# #978 验证设计与追踪矩阵

**状态：设计，全部业务用例未执行。** 本轮没有业务代码、数据库迁移、真实 SMTP、浏览器或部署验收。以下是实施后的验收依据，不是“已经通过”的测试报告。

## 1. 源码基线与事实入口

基线 `4811b96129802be5028d5e4bc1b7b6a158b5b7bb`；设计范围为 `proposal.md` 所列模块。

| 源码路径 | 当前事实 | 覆盖需求 |
|---|---|---|
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/NotificationEventListener.java` | 提交后异步监听；订阅同属 PUBLISH；多种审核/提升/举报事件 | REQ-001/REQ-003/REQ-005/REQ-006 |
| `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationService.java` | IN_APP 关闭提前返回 | REQ-001/REQ-005 |
| `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationPreferenceService.java` | 默认 true，只支持站内布尔值 | REQ-001/REQ-004 |
| `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationCategory.java` | 四个现有类别 | REQ-001/REQ-003 |
| `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/service/NotificationCleanupTask.java` | 站内已读/未读独立清理 | REQ-009 |
| `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/user/UserAccount.java` | 无独立邮件证明和地址修订号 | REQ-010/REQ-012/REQ-013 |
| `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/identity/IdentityBindingService.java` | 外部可信 claim 可写 email，未存通用持久证明 | REQ-010/REQ-012 |
| `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/merge/AccountMergeService.java` | 合并可替换主账号邮箱 | REQ-012 |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/bootstrap/BootstrapAdminInitializer.java` | 初始化可改邮箱 | REQ-012 |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/bootstrap/LocalDevDataInitializer.java` | 本地初始化同样是邮箱写入点 | REQ-012 |
| `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/local/PasswordResetService.java` | 已有 SMTP，但验证码用途是重置密码 | REQ-003/REQ-010/REQ-011 |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/NotificationPreferenceController.java` | 两个既有站内偏好 API 别名 | REQ-001/REQ-009 |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/task/ScanTaskOutboxDispatcher.java` | 既有租约/调度参考，不等于邮件实现 | REQ-006/REQ-007 |
| `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/ScanTaskOutboxJpaRepository.java` | 可参考真实数据库领取模式 | REQ-006 |
| `server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/settings/SystemSetting.java` | 系统普通配置可复用的持久模型 | REQ-009 |

## 2. 正常流程与变更流程

下表每一行均须在实施后保存请求/操作、数据、原始结果及最终状态；同方法的数据变体可合并执行，但不能把正常回归借作新功能证据。所有状态当前均为 **计划/未执行**。

| 用例 | 轨道/场景 | 需求 | 数据和动作 | 独立判据/最终状态 | 验证层 |
|---|---|---|---|---|---|
| TC-001 | 正常：发布→审核→站内查看；SCN-002 | REQ-001/REQ-005 | 邮件总开关关，复测四类站内消息及已读/删除 | 原接收者、分类、权限、站内偏好完全不变；无 SMTP | 集成+浏览器 |
| TC-002 | 正常：密码重置；SCN-007 | REQ-003/REQ-011 | 所有业务邮件 OFF/DIGEST，走原重置流程 | 原安全码流程保持可用，不能交叉消费通知验证 | 集成+捕获器 |
| TC-003 | 模式矩阵；SCN-001/SCN-002/SCN-006 | REQ-001/REQ-003 | 四类×三模式，IN_APP on/off，空偏好 | 邮件默认 OFF、渠道独立；即时不等窗口，旧 API 不变 | 参数化服务+API |
| TC-004 | 摘要主路径；SCN-003 | REQ-002 | 同用户 12 更新+3审核，另有另一用户 | 一人一个窗口一批次，15项两节，另一用户独立 | PostgreSQL+SMTP |
| TC-005 | 摘要上下界；SCN-004 | REQ-002 | 0/1/49/50/51/大量项、UTC窗口边界；站内OFF+51项及站内记录删除 | 空不发；超过50仍一批次且总数准确；独立邮件详情分页可见合法其余项，过期明确提示且不串用户；窗口边界无重叠 | 可控时钟+集成+浏览器 |
| TC-006 | 封口竞争；SCN-005 | REQ-002/REQ-006 | 两线程入箱与封口、长事务迟到 | 固定成员不变，迟到项可接纳、不丢、不重开旧批次 | PostgreSQL并发屏障 |
| TC-007 | 变更/关闭；SCN-008/SCN-009/SCN-027 | REQ-004/REQ-013 | OFF、总开关关闭、模式互换、周期变更、再开启 | 未发项正确抑制、不历史重放；旧窗口按明确快照规则完成 | 状态机+集成 |
| TC-008 | 事务隔离；SCN-010/SCN-011 | REQ-005 | 业务回滚、站内成功邮件入箱失败、SMTP故障 | 回滚无任务；已提交业务/站内不被邮件失败撤销；记录入口局限 | 集成+故障注入 |
| TC-009 | 实例去重；SCN-012 | REQ-006 | 同事件重放、同技能不同版本、合法重复生命周期；接纳窗口前/边界/后和清理后重放 | 窗口内同事件仅一项，不同合法实例均存在；过期重放拒绝，occurredAt不被重设；不承诺永久账本 | 参数化+PostgreSQL |
| TC-010 | 两 worker 竞争；SCN-013 | REQ-006 | 相同due批次、CLAIMED/SENDING租约过期、旧token、网络阻塞 | 单owner，旧worker不能确认；未授权可回收，已授权过期UNKNOWN；SMTP等待不占DB长事务 | 多实例集成 |
| TC-011 | 重试边界；SCN-014 | REQ-007 | 临时拒绝→恢复、永久拒绝、最大尝试次数 | 退避时间与终态正确，恢复不新建重复逻辑批次 | 可控时钟+SMTP协议夹具 |
| TC-012 | 不确定发送；SCN-015 | REQ-007 | DATA后断连、确认前崩溃、租约到期、人工重试 | UNKNOWN不盲目重发；管理员重试审计且说明重复风险 | SMTP故障+进程故障注入 |
| TC-013 | 资格变化；SCN-016/SCN-026 | REQ-008/REQ-013 | 退订、退出空间、撤角色、隐藏/删除资源、账号停用 | 不合格项抑制，其他项可投递，旧权限不能泄露内容 | 业务集成 |
| TC-014 | 模板与隔离；SCN-017 | REQ-008 | 同邮箱不同账号、HTML/换行、恶意Host/URL、子路径 | 不混信、不含敏感正文、链接白名单且重新鉴权 | 模板+API+浏览器 |
| TC-015 | 系统设置权限；SCN-018 | REQ-009 | 普通/空间管理员/超级管理员、缺SMTP、配置冲突 | 仅超管可写/重试；默认关闭；版本冲突不覆盖新值 | API权限+集成 |
| TC-016 | 清理与升级；SCN-019 | REQ-009 | 站内删除、已读/未读清理、邮件保留期边界与清理后同事件重放；新应用验邮→回退旧应用改址→再升级 | 正常清理不删未完成队列；保留期不得短于接纳窗口，旧事件清理后不能重入箱；回退维护明确抑制队列/失效证明，旧应用改址后不得恢复旧验邮资格，业务数据及站内兼容 | 迁移+集成 |
| TC-017 | 正常验邮；SCN-020 | REQ-010 | 有/无当前邮箱，正确挑战，已验证后开启类别 | 只对当前账号当前地址产生证明，无邮箱不发备用地址 | API+SMTP+浏览器 |
| TC-018 | 主体与用途；SCN-021 | REQ-010/REQ-011 | 匿名、另一用户、伪造userId/收件地址、重置码混用 | 全部拒绝，目标账号/邮箱/角色/会话不变 | 安全负向API |
| TC-019 | 挑战边界；SCN-022 | REQ-011 | 到期前/点上/后、错误4/5/6次、冷却59/60秒、限流 | 明确上限，旧码失效，明文码不落库/日志 | 可控时钟+安全检查 |
| TC-020 | 挑战并发；SCN-023 | REQ-011 | 双确认、重发与确认、SMTP失败 | 仅最新挑战可消费一次，发送失败不等于已验证 | PostgreSQL并发+SMTP |
| TC-021 | 地址修订；SCN-024/SCN-025 | REQ-012 | A→B→A、同地址重存、外部同步/初始化/账号合并 | 所有写入遵循修订号，旧证明失效；不迁移次账号队列/偏好 | 单元+跨模块集成 |
| TC-022 | 队列中换址；SCN-026/SCN-027 | REQ-013 | 入箱后换邮箱、停用/合并/OFF与发送授权双向并发屏障、重新验邮 | 变更先提交则拒绝授权且零SMTP；授权先提交则仅该在途尝试可能发送、后续尝试重查；不改投未验新址，不补历史事件 | 状态机+并发集成 |

## 3. 八类覆盖决定

| 类别 | 计划覆盖 | 说明 |
|---|---|---|
| 主路径 | TC-001/002/003/004/017 | 正常回归与邮件新流程分别举证 |
| 边界 | TC-005/007/019 | 窗口、数量、有效期、错误次数、冷却 |
| 负向 | TC-008/018/019 | 回滚、错误码、非法主体与用途 |
| 特殊/兼容 | TC-003/014/016/021 | 老用户默认值、旧客户端、别名、地址写入与部署子路径 |
| 失败/恢复 | TC-008/011/012/020 | 确定失败恢复与UNKNOWN分别验证，不混称恰好一次 |
| 权限/安全 | TC-013/014/015/018 | 当前资格、越权、最小内容及收件人隔离 |
| 状态迁移 | TC-007/010/012/016/021/022 | 持久队列、开关、邮箱修订及清理终态 |
| 并发/异步 | TC-006/009/010/012/020/021 | 封口、去重、领取、崩溃、挑战与邮箱写入竞争 |

## 4. 六维输入与组合选择

| 维度 | 代表值 | 对应测试 |
|---|---|---|
| 角色/权限 | 匿名、普通用户、技能作者、订阅者、空间管理员、超级管理员、停用/合并账号 | 003/013/015/018/022 |
| 初始状态 | 全新/旧账号、未验/已验/失效、空队列、收集中/已封口/租约中/重试/UNKNOWN/终态 | 005/006/007/010/012/017/021 |
| 输入数据 | 四类别×三模式；0/1/50/51/大量项；单/多技能；同/不同用户；空/恶意文本；地址往返 | 003/004/005/009/014/019/021 |
| 依赖故障 | SMTP连接拒绝/临时/永久/接受未知，数据库入箱失败，进程崩溃 | 008/010/011/012/020 |
| 时间/并发 | UTC窗口边界、延迟入箱、租约到期、确认/重发竞争、邮箱/权限变更与发送竞争 | 005/006/010/012/019/020/021/022 |
| 配置/兼容 | 总开关、SMTP缺失、部署初始化与DB优先级、旧站内API、子路径、中英文、双实例 | 001/003/007/014/015/016 |

不展开全部笛卡尔积：四类×三模式做完整参数化；影响资格的“总开关/邮箱有效/类别非OFF/账号与资源资格”用决策表逐项翻转证明拒绝；多实例和SMTP不确定性单独测试，不能用mock happy path代替。

## 5. 完成门禁和限制

- 设计批准后才实施；每个 REQ 必须有当前 SHA 的 passed 用例，不能靠本文“计划覆盖”通过发布门禁。
- 真实 PostgreSQL 验证唯一键、事务、并发领取与封口；可控时钟验证窗口/重试；本地 SMTP 捕获器验证封数、收件人、内容及失败协议。
- UI/设置/验邮需在精确 SHA 的标准 Compose 预览上用浏览器交互验证，检查页面、网络、最终数据库状态及无未解释错误。
- 实现时按项目 Makefile 运行相关后端/前端/类型生成检查，扩大范围用于共享邮箱修订与事件结构变化；源码测试、镜像启动、SMTP接受、真实收件箱送达分别报告。
- 业务提交至异步入箱前的崩溃窗口仍未闭合，应以故障分析和实际边界记录，而非把它列成“已保证不丢失”。
- 当前没有真实 SMTP 凭证联调、企业邮箱送达、生产部署、性能实测，也没有已实现的新接口/配置；本轮仅可声明文档结构校验与独立设计审查结果。

## 6. 本轮方案检查结果（2026-10-22）

| 检查 | 实际结果 | 证据边界 |
|---|---|---|
| `openspec validate add-notification-email-delivery --strict` | 通过 | 证明规格结构有效，不证明实现正确或产品方案已批准 |
| 追踪与源码引用检查 | 13个唯一REQ、27个唯一SCN全部显式映射到22组计划用例；15个完整源码路径均存在 | 是方案完整性检查，22组业务用例仍全部未执行 |
| 新文档空白检查、实施状态检查 | 6份Markdown无空白错误，实施任务全部保持未勾选 | 对未跟踪新文件使用 `git diff --no-index --check` 检查；其正常差异退出码与空白诊断分开判断 |
| 独立技术方案审查 | `trellis-check /root/review997` 完成一次全文审查及一次仅针对修订的复核；3项major均闭合，无剩余major | 不代替实施后的代码Review、测试、SMTP及运行验收 |

本轮审查及修订：

1. 明确 CLAIMED 与 SENDING 授权线性点、统一锁序、换址/关闭在边界两侧的结果，以及 SENDING 租约过期只能保守进入 UNKNOWN；同步 REQ-004/006/008/013 和 TC-010/022。
2. 摘要溢出改用邮件独立的分页详情，不依赖站内消息开启或保留；同步 REQ-002、候选API、页面任务和 TC-005。
3. 去重改为不可变发生时间与有限历史接纳窗口，保留期限覆盖窗口，清理后的旧事件不能重新入箱；同步 REQ-006、TC-009/016 和任务。
4. 自查补齐旧应用回退不维护邮箱修订号的边界：回退前停worker、使通知证明/挑战失效并抑制队列，再升级不恢复旧证明。

下一门禁是用户评审技术取舍及推荐参数；尚未获得业务实现、发布或外部发信授权。方案文件在独立工作分支保留，未提交、未推送。
