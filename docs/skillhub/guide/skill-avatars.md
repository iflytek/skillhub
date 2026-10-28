# 技能头像：上传、审核与供应商配置

本页介绍如何上传技能头像，以及部署方如何接入讯飞或阿里云图片审核。这是**技能头像**功能，不涉及用户个人头像；图片审核与技能包安全扫描是两种不同的检查。

## 技能所有者怎么使用

1. 新增技能时，在发布页选择自定义 PNG/JPEG；已有技能从「我的技能 → 更新」进入，也可只改头像而不发布新版本。
2. 在裁剪弹窗调整方形区域和缩放，确认后等待上传成功。图片上传中或失败时不能提交；未选择头像也可正常提交，显示技能名首字母。
3. 提交后，在「我的技能」或更新页查看**本次提交的图片**及审核状态。审核通过前，其他人继续看到旧头像或首字母。
4. 被拒绝后查看原因，修改图片再提交。新申请会替换旧待审申请，旧审核结果不会覆盖新头像。

上传输入限 PNG/JPEG、2 MB、正方形且边长不超过 1024 像素；服务器会再次校验并重新编码为 PNG。上传成功仅表示图片已存入私有存储，不代表审核通过。临时上传引用 24 小时后过期，过期需要重新上传。

更新默认保留原头像，也可移除：单改头像提交成功后恢复首字母；与新技能包一起提交时，审核通过后才移除。取消编辑或提交失败不改变现有头像。技能详情和列表展示当前生效头像，不设单独的头像管理入口。

## 管理员怎么审核

头像与技能包同时提交时，共用一张技能申请，审核员在原审核详情中对内容和头像一次批准或拒绝。只改头像且需要人工决定时，也进入这个技能审核队列，标明「头像修改」，不创建新技能版本。

团队管理员审核本团队；平台技能管理员和超级管理员可审核团队及全局，自审权限沿用原技能审核规则。管理员可撤下头像，但不能替所有者上传新头像。

### 两个开关与四种流程

超级管理员在「系统配置」（`/dashboard/admin/system-config`）中选择「启用机器审核」与「启用人工审核」。默认机器关闭、人工开启。后台只管理这两个开关，不配置供应商、不显示密钥。

| 机器审核 | 人工审核 | 非 PRIVATE 技能单改头像的流程 |
| --- | --- | --- |
| 关闭 | 关闭 | 提交成功后直接生效 |
| 关闭 | 开启 | 人工批准后生效 |
| 开启 | 关闭 | 机器通过后自动生效；阻止或异常时不生效 |
| 开启 | 开启 | 先机器审核，再由人工决定 |

**边界：**`PRIVATE` 技能头像仅所有者可读，直接使用，不运行上述机器或人工审核；扩大可见范围时需要按目标范围重新审核。头像与技能包同次提交时，不论头像开关如何，仍须等待原技能审核共同批准，不会提前发布头像。

### 违规会直接失败吗？

机器结果与申请最终决定不同：

| 结果 | 处理 |
| --- | --- |
| `PASSED`（通过） | 有人工审核时继续待审；没有人工审核时单改头像自动生效 |
| `BLOCKED`（阻止） | 新图片保持私有、旧头像继续使用。开启人工审核时继续待审，审核员可拒绝；认为误判时必须填写明确理由才能批准。未开启人工审核时不生效，所有者可申请人工复核 |
| `ERROR`（异常） | 新图片保持私有，不能直接批准放行；先排查配置、网络或供应商服务，再重试 |

因此，**机器判违规不等于文件上传失败，也不等于人工已拒绝申请**。人工拒绝后，所有者可查看提交图片与拒绝原因。异常自动重试有一小时冷却，同一候选最多自动调用三次（含首次）；耗尽后可在冷却结束后主动重试或重新提交。

## 部署方如何接入机器审核

### 1. 选择一个供应商

每个服务部署只选择一个：`xfyun` 或 `aliyun`。供应商及凭证在应用启动时读取；后台仅能启停审核，不能切换供应商。不开启机器审核时无需提供云审核凭证。

| 供应商 | 需要开通的服务 | 所需凭证 |
| --- | --- | --- |
| 讯飞 | 图片合规审核 | APPID、APIKey、APISecret |
| 阿里云 | 内容安全增强版，上海地域的头像图片检测 `profilePhotoCheck` | RAM 用户的 AccessKey ID、AccessKey Secret |

服务开通、额度及计费以供应商控制台为准；开启后真实调用可能产生费用。

- 讯飞：[官方图片合规服务接入与鉴权说明](https://www.xfyun.cn/doc/nlp/ImageModeration/API.html)。注意 APIKey 对应 `access-key-id`，APISecret 对应 `access-key-secret`。
- 阿里云：[内容安全控制台](https://yundun.console.aliyun.com/?p=cts)、[开通服务与创建 RAM 用户的官方接入步骤](https://help.aliyun.com/zh/document_detail/467828.html)。按最小权限配置 `yundun-greenweb:DescribeUploadToken` 和 `yundun-greenweb:ImageModeration`，不需要给审核用户自有 OSS 全权限。

阿里云适配器通过官方临时上传令牌上传私有图片，再调用同步审核，**无需自建审核用 OSS Bucket**。它与 SkillHub 自己保存技能和头像的对象存储配置相互独立。当前将 `RiskLevel=none/low` 视为通过，`medium/high` 视为阻止，其中 `medium` 转人工复核是 SkillHub 的策略。空结果、未知值及接口失败均不放行。参考[官方接口说明](https://help.aliyun.com/document_detail/467829.html)。

### 2. 用私有环境文件提供凭证

下面使用仓库标准 `compose.release.yml` 的 `server` 服务。先按现有部署流程准备 `.env.release`、数据库及 SkillHub 对象存储，选用包含本功能的镜像版本。本节不会替换原部署设置。

在**仓库外**建立私有目录，将下面路径中的 `/etc/skillhub/private` 换成实际路径；目录权限建议 `700`，文件权限 `600`。不要把凭证文件提交到 Git。

标准 Compose 只透传它声明的环境变量；仅向 `.env.release` 添加新审核属性不会自动注入 `server`。以下通过额外 override 的 `env_file` 显式注入，原 Compose 模板没有内置这些凭证字段。

创建 `/etc/skillhub/private/avatar-moderation.env`，以下两种内容**二选一**。占位符需替换为实际凭证；`SPRING_APPLICATION_JSON` 必须是单行合法 JSON。

**讯飞：**

```dotenv
SPRING_APPLICATION_JSON={"skillhub":{"avatar":{"moderation":{"provider":"xfyun","xfyun":{"app-id":"YOUR_APPID","access-key-id":"YOUR_APIKEY","access-key-secret":"YOUR_APISECRET"}}}}}
```

**阿里云：**

```dotenv
SPRING_APPLICATION_JSON={"skillhub":{"avatar":{"moderation":{"provider":"aliyun","aliyun":{"access-key-id":"YOUR_ACCESS_KEY_ID","access-key-secret":"YOUR_ACCESS_KEY_SECRET"}}}}}
```

如果部署已有 `SPRING_APPLICATION_JSON`，**将上述 `skillhub.avatar.moderation` 合并进原 JSON，不要覆盖原配置或设置重复变量**；其他 override 若通过 `environment` 定义同名变量，会优先于 `env_file`，也需同步合并。特殊字符应按 JSON 格式转义。

在同一私有目录创建 `compose.avatar.yml`：

```yaml
services:
  server:
    env_file:
      - /etc/skillhub/private/avatar-moderation.env
```

然后在仓库根目录执行。已有部署若使用额外 Compose 文件、`-p` 项目名或其他启动参数，保留这些参数，再追加头像 override，确保更新原服务：

```bash
make validate-release-config
docker compose --env-file .env.release \
  -f compose.release.yml \
  -f /etc/skillhub/private/compose.avatar.yml \
  up -d --no-deps server

docker compose --env-file .env.release \
  -f compose.release.yml \
  -f /etc/skillhub/private/compose.avatar.yml \
  ps server
```

上述更新命令适用于已运行的完整栈。首次部署完整栈时，将 `up -d --no-deps server` 改为 `up -d`。只执行 `restart` 不会重新加载修改后的容器环境，配置更新需要重新创建服务。后续更新也要保留同一 override。

正式 Spring 属性是 `skillhub.avatar.moderation.provider`、`skillhub.avatar.moderation.xfyun.app-id`、`skillhub.avatar.moderation.xfyun.access-key-id`、`skillhub.avatar.moderation.xfyun.access-key-secret`，以及 `skillhub.avatar.moderation.aliyun.access-key-id`、`skillhub.avatar.moderation.aliyun.access-key-secret`。其他部署平台可通过 Secret 注入同等属性。

::: warning 讯飞出口日志
讯飞官方接口将 `accessKeySecret` 放在请求 URL 参数中。应用不记录完整 URL 或异常原文，但部署方仍需关闭 HTTP 客户端调试日志，并确保出口代理、链路追踪及第三方日志不采集该请求 URL。不要把含凭证的 Compose 展开结果、容器环境或签名请求 URL 分享给其他人。
:::

### 3. 在后台开启，并验证真实调用

1. 确认 `server` 健康后，以超级管理员进入「系统配置」。
2. 开启机器审核，建议初次接入时保留人工审核开启。
3. 用技能所有者账号对一个非 `PRIVATE` 测试技能提交**新头像**。
4. 在更新页查看机器结果；开启人工时，审核员在技能审核详情中确认图片、机器状态并做出决定。
5. 对通过、阻止、异常分别检查：通过前旧公开头像不变，阻止或异常的新图片不可公开，人工拒绝后可见原因。不要为了测试把违规图片批准公开。

填写凭证、成功保存开关或应用健康，**都不能证明云服务调用成功**。后台检查的是配置是否齐全，真正的服务权限、网络、余额和接口响应必须通过新申请验证。已有申请不因开关开启而补跑机器审核。

供应商接口的代码与模拟测试不能替代本部署的账号验收。每次启用或轮换后都应验证实际账号的连通性、结果映射和失败行为。

## 修改开关与切换供应商

申请保存提交时的机器、人工开关和供应商快照，之后改配置不会回写已有申请。若要切换供应商，应更新私有配置并重新创建 `server`，再提交新申请：

- 已完成的机器结果不会自动重新检测。
- 旧候选仍需调用旧供应商时，切换后会得到 `ERROR`，不会偷偷改用新供应商；应重新提交以采用新供应商。
- 配置切换前先处理待审申请，避免把旧任务留在不可继续审核的状态。

被拒绝、替换或删除的候选图片私有保留 30 天后清理，文字审核记录继续保留。云审核服务自己的临时图片生命周期与 SkillHub 私有保留规则不同。

## 常见问题

**开启失败提示供应商不可用？** 检查 `provider` 拼写、所选供应商凭证是否齐全，以及额外 override 是否实际用于启动 `server`。不要通过输出完整环境变量排查密钥。

**上传成功但一直异常？** 检查服务器能否访问对应云接口、凭证权限、服务是否开通及可用额度；阿里云还需要能访问其临时 OSS 上传端点。等待冷却后重试，不要把异常当通过。

**人工开关关闭就不审核技能包了吗？** 不是。这个开关只控制头像的人工策略，不改变原技能包审核流程。

**为什么其他人仍看到旧头像？** 新申请未通过时这是正常行为；所有者应到「我的技能」或更新页查看提交图片，公开详情只展示当前生效头像。
