# 有界读取与接续

适用于实施、检查、换对话和真实压缩后的工程/设计规范读取。规范正文仍在原文件；任务记录只保存来源、原因、版本、进度和缺口。OpenSpec拥有正式需求/任务，不另建规划。

## 读取

Agent选择本次适用资料，一次只读一个文件或组件，将每页作为独立工具结果返回；不要合并cat、多个大JSON或只打印摘要/哈希。执行器输出预算至少4000 tokens，仍观察宿主是否截断。

```sh
python3 scripts/read-context.py file .trellis/spec/frontend/index.md
python3 scripts/read-design-system.py component button
```

优先共享scripts入口；旧分支缺入口时可用本机同步的.trellis/scripts同名工具。默认1800字符、最大2400，按UTF-8/JSON大小自动缩短。续页使用同一查询、返回的nextOffset和payloadSha256：`--offset N --expect-sha256 HASH`。读取器拒绝超大请求和版本变化；不是把规则删短。设计theme/spec也默认分页。每页须看见完整text与末尾pageSha256；外层截断时缩小重读，未取完前不依赖相关规则实施。

## 接续与检查

- 在已有任务复用implement.jsonl/check.jsonl的适用文件清单；需要设计查询时可另存`context-sources.json`，它是来源清单，不是第二套任务。无任务不强制创建。
- JSON来源清单是数组：`{"file":".trellis/spec/frontend/index.md","reason":"前端入口"}`；设计项用`{"design":{"command":"component","names":["button"]},"reason":"按钮契约"}`。两种二选一，设计库沿用当前本地/私有快照。
- 新对话或压缩后，先读任务进度和清单，再重取当前依赖的必要正文。来源变动、任务范围变动要调整清单及受影响验收，不照搬旧“已读”结论。
- 只读任务无需写清单，可用 `python3 scripts/read-context.py check --source PATH --design "component button"`；默认只定位当前CODEX_THREAD_ID日志。使用functions.exec时一次只输出一页；需要循环时以 `notify(r.output)` 即时返回每页，不能累计多个大结果再text输出。
- 有可读Codex原始日志时由Agent运行`python3 scripts/read-context.py check SOURCE_LIST --transcript SESSION_JSONL`。仅核对实际工具返回的当前正文或完整分段；默认排除最后一次compacted之前记录，可用`--after-line N`进一步限定检查阶段。来源变动、缺段、篡改或空清单不会通过。缺项补读后重查；若专题声明 `## 必需上下文`，检查器自动展开该节的本地完整文件链接并核对正文，循环去重；该节也可用json代码块声明设计查询数组，沿用来源清单格式。不递归所有参考链接。清单自身是否选对仍需审查。
- 工具只证明日志中观察到正文，不证明理解、遵循或宿主禁止写入。日志不可读则保留“送达未验证”，逐页补读并报告，不改信任、不借别的线程或检查器输出充当新正文。

任务开始、恢复、检查使用同组适用规则。库check成功不等于设计正文送达，全文送达不等于实现通过；实际测试和浏览器验收另行记录。
