# 配置头像机器审核服务

本页面向具有部署权限的操作者，说明头像图片审核供应商的服务开通、凭据注入、服务更新与实际调用核验。部署前须已有可运行的注册中心及包含本功能的镜像；本页不部署独立审核引擎，不说明技能所有者的上传操作。头像提交、策略与审核入口见[技能头像与审核](./skill-avatars.html)。


## 前置条件与职责

| 操作 | 执行主体 | 前置与完成依据 |
| --- | --- | --- |
| 注入凭据和更新容器 | 具有部署权限的操作者 | 原注册中心正常运行，能够使用其部署目录和私有配置；更新的是原项目服务 |
| 开启审核策略 | 超级管理员（SUPER_ADMIN） | 配置齐全后在系统配置保存开关；保存成功不代表云服务调用成功 |
| 产生验收候选 | 技能所有者 | 向非私有测试技能提交新头像；不复用旧申请推断接入结果 |
| 核对审核与公开结果 | 有权技能审核人 | 在原技能申请中核对图片及机器结果；不得为验收批准违规内容 |

## 选择供应商并确认权限

每个服务部署只选择一个：`xfyun` 或 `aliyun`。供应商及凭证在应用启动时读取；后台仅能启停审核，不能切换供应商。不开启机器审核时无需提供云审核凭证。

| 供应商 | 需要开通的服务 | 所需凭证 |
| --- | --- | --- |
| 讯飞 | 图片合规审核 | APPID、APIKey、APISecret |
| 阿里云 | 内容安全增强版，上海地域的头像图片检测 `profilePhotoCheck` | RAM 用户的 AccessKey ID、AccessKey Secret |

服务开通、额度及计费以供应商控制台为准；开启后真实调用可能产生费用。

- 讯飞：[官方图片合规服务接入与鉴权说明](https://www.xfyun.cn/doc/nlp/ImageModeration/API.html)。APIKey 对应 `access-key-id`，APISecret 对应 `access-key-secret`。
- 阿里云：[内容安全控制台](https://yundun.console.aliyun.com/?p=cts)、[开通服务与创建 RAM 用户的官方接入步骤](https://help.aliyun.com/zh/document_detail/467828.html)。按最小权限配置 `yundun-greenweb:DescribeUploadToken` 和 `yundun-greenweb:ImageModeration`，不需要给审核用户自有 OSS 全权限。

阿里云适配器通过官方临时上传令牌上传私有图片，再调用同步审核，**无需自建审核用 OSS Bucket**。它与 SkillHub 自己保存技能和头像的对象存储配置相互独立。当前将 `RiskLevel=none/low` 视为通过，`medium/high` 视为阻止，机器阻止后的人工处理遵循头像审核策略，不代表供应商已经作出最终人工决定。空结果、未知值及接口失败均不放行。参考[官方接口说明](https://help.aliyun.com/document_detail/467829.html)。

## 注入私有配置并重新创建服务

下面使用仓库标准 `compose.release.yml` 的 `server` 服务。先按现有部署流程准备 `.env.release`、数据库及 SkillHub 对象存储，选用包含本功能的镜像版本。本节不会替换原部署设置。

在仓库外建立私有目录，将示例路径 `/etc/skillhub/private` 换成实际路径。以下是本页采用的操作系统权限示例，不是应用配置默认值；不得把凭据文件提交到 Git。

| 操作对象 | 本页权限示例 | 生效方式与边界 |
| --- | --- | --- |
| 私有目录 | `700` | 由部署方设置文件系统权限，仅目录所有者访问 |
| 凭据与 override 文件 | `600` | 由部署方设置文件系统权限，仅文件所有者读写 |


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

## 开启审核并核验实际结果

1. 确认 `server` 健康后，以超级管理员进入「系统配置」。
2. 开启机器审核，初次接入时保留人工审核开启，以便核对机器结果与人工决定。
3. 用技能所有者账号对一个非 `PRIVATE` 测试技能提交**新头像**。
4. 在更新页查看机器结果；开启人工时，审核员在技能审核详情中确认图片、机器状态并做出决定。
5. 对通过、阻止、异常分别检查：通过前旧公开头像不变，阻止或异常的新图片不可公开，人工拒绝后可见原因。不要为了测试把违规图片批准公开。

填写凭证、成功保存开关或应用健康，均不能证明云服务调用成功。后台检查的是配置是否齐全，真正的服务权限、网络、余额和接口响应必须通过新申请验证。已有申请不因开关开启而补跑机器审核。

供应商接口的代码与模拟测试不能替代本部署的账号验收。每次启用或轮换后都应验证实际账号的连通性、结果映射和失败行为。

## 切换供应商与已有申请

每次申请保存提交时的机器、人工开关和供应商快照。后续配置变更不会回写已有申请，也不会重跑已完成结果。切换前，先由有权审核人处理待审申请；更新私有配置、重新创建 `server` 后，由技能所有者提交新申请以使用新供应商。

旧候选仍需调用旧供应商时，切换后会得到 `ERROR`，不会改用新供应商。不得仅更新后台开关后认定旧任务已经恢复。SkillHub 候选图片保留规则见[技能头像与审核](./skill-avatars.html)；供应商临时上传图片的生命周期属于外部服务规则，二者不能互相替代。

## 结果确认与失败处置

后台允许开启机器审核，仅表示所选供应商的必要配置齐全。完成接入须以新申请的实际机器结果和候选图片可见性为依据；服务健康、填写凭证与保存开关均不能替代真实调用核验。

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 开启时提示供应商不可用 | `provider` 拼写错误或凭据未注入 | 核对私有文件中的属性名及启动命令是否包含相同 override | 修正配置并重新创建原服务；不要输出完整环境变量或 Secret |
| 配置修改后行为未变 | 仅重启容器，或同名变量被覆盖 | 比较实际启动使用的 Compose 文件顺序及 `environment`/`env_file` 定义 | 合并同名 JSON 并重新创建服务；不要覆盖其他 Spring 配置 |
| 新申请机器结果为 `ERROR` | 服务未开通、权限、额度或出口网络异常 | 核对新申请机器状态及供应商控制台权限、服务和额度；阿里云还需检查临时 OSS 上传端点可达性 | 恢复依赖后按冷却规则重试；不要将异常当作允许 |
| 切换后旧候选持续异常 | 申请保存的供应商与当前部署不一致 | 比较候选所用供应商和当前 `provider` | 处理旧申请或重新提交；不要直接修改候选快照 |
| 开启机器审核后旧申请没有新结果 | 开关不追溯已有申请 | 在更新页核对申请时间、机器状态及本次候选 | 使用新申请验证；不要为生成调用重复批准旧申请 |

配置绑定及供应商选择依据：[供应商网关](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AvatarModerationGateway.java)、[讯飞适配器](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/XfyunAvatarModerationProvider.java)和[阿里云适配器](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AliyunAvatarModerationProvider.java)。本页命令以[release Compose](https://github.com/iflytek/skillhub/blob/main/compose.release.yml)为基线；其他部署路径须通过其 Secret 机制注入相同属性，不得直接套用 Compose 更新命令。
