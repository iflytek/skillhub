# 云存储链接内置 Skills 配置指南

本文说明如何通过仓库内 manifest 配置 SkillHub 内置 Skills，以及应用启动时这些 Skills 如何从云存储同步到 `@global` 空间。

适用场景：

- 希望 SkillHub 新部署实例默认带有一批官方内置 Skills。
- 希望内置内容的来源、许可证和修改可以在开源仓库中审查。
- 内置 Skill 包已经上传到官方可控的云存储域名。

## 1. 方案概览

内置 Skill 的审查后源码维护在仓库的 `builtin-skills/skills/` 中，但运行时不直接读取这些
目录。发布流程先生成确定性 zip 并上传云存储；应用启动时根据 manifest 中的 URL 下载制品，
再通过 SkillHub 现有发布链路发布到 `@global`。

流程：

```text
维护审查后源码 -> 校验与打包 -> 上传不可变制品 -> 更新 manifest -> 构建/部署 SkillHub 镜像 -> 应用 ready -> 下载并校验 zip -> 发布到 @global
```

核心文件：

```text
builtin-skills/catalog.json
builtin-skills/evals.json
builtin-skills/skills/<slug>/
scripts/build-builtin-skills.py
server/skillhub-app/src/main/resources/builtin-skills/manifest.json
```

manifest 需要维护四个字段：

- `slug`：Skill 在 `@global` 下的 slug。
- `version`：期望同步的 Skill 版本。
- `url`：Skill zip 包的云存储 HTTPS 链接。
- `sha256`：发布制品内容的 SHA-256，小写 64 位十六进制字符串。它是完整性依据，
  不要求 URL 的文件名必须等于 SHA-256；CDN 自动生成 UUID 文件名也可以使用。

## 2. Manifest 配置

manifest 文件格式如下：

```json
{
  "skills": [
    {
      "slug": "skillhub-hello",
      "version": "1.0.0",
      "url": "https://bjcdn.openstorage.cn/aicontest/2026-06-11/f8a59af3-30d4-4031-80f6-ebff74b05195.zip",
      "sha256": "acb591ed0891e735c364b955f5b94b2b9ce567c1d9e347312cebfbfde2d93f57"
    }
  ]
}
```

可以配置多个 Skills，也可以为同一个 `slug` 配置多个版本：

```json
{
  "skills": [
    {
      "slug": "skillhub-hello",
      "version": "1.0.0",
      "url": "https://bjcdn.openstorage.cn/<path-to-builtin-skill-zip>/skillhub-hello-1.0.0.zip",
      "sha256": "<sha256-of-skillhub-hello-1.0.0.zip>"
    },
    {
      "slug": "skillhub-hello",
      "version": "1.1.0",
      "url": "https://bjcdn.openstorage.cn/<path-to-builtin-skill-zip>/skillhub-hello-1.1.0.zip",
      "sha256": "<sha256-of-skillhub-hello-1.1.0.zip>"
    },
    {
      "slug": "skillhub-guide",
      "version": "1.0.0",
      "url": "https://bjcdn.openstorage.cn/<path-to-builtin-skill-zip>/skillhub-guide-1.0.0.zip",
      "sha256": "<sha256-of-skillhub-guide-1.0.0.zip>"
    }
  ]
}
```

配置要求：

- `skills` 必须是数组。
- 每一项必须同时填写 `slug`、`version`、`url`、`sha256`。
- `slug` 必须符合 SkillHub slug 规则。
- `sha256` 必须是小写 64 位十六进制字符串，并与 URL 返回的原始 zip 字节一致。
- `url` 最后路径段必须是 `.zip` 文件；文件名可以是 SHA-256、UUID 或 CDN 生成的不可变名称。
- 同一个 `slug + version` 重复出现时，只处理第一条，后续重复项会被跳过。
- manifest 最多处理前 100 条 entries。
- 同一个 `slug` 的多个版本建议按从旧到新的顺序排列；运行时按 manifest 文件顺序处理，不做自动版本排序。

## 3. Skill 包要求

manifest 中的 `url` 必须指向 zip 包。zip 包需要满足 SkillHub Skill 包协议：

- zip 可以在根目录直接包含 `SKILL.md`，也可以包含一个单独的顶层 Skill 目录，并在该目录下包含 `SKILL.md`。
- `SKILL.md` frontmatter 中必须包含合法的 `name`、`description`、`version` 等元数据。
- `SKILL.md` 中的 `name` 经过 slug 归一化后，必须等于 manifest 中的 `slug`。
- `SKILL.md` 中的 `version` 必须等于 manifest 中的 `version`。
- 包内容仍会经过 SkillHub 现有发布校验，包括文件数量、文件大小、扩展名、文件类型等规则。

示例：

```text
skillhub-hello-1.0.0.zip
├── SKILL.md
├── LICENSE.txt
├── NOTICE.md
└── scripts/
    └── check.js
```

同样支持标准单目录 Skill 包：

```text
skillhub-hello-1.0.0.zip
└── skillhub-hello/
    ├── SKILL.md
    ├── LICENSE.txt
    └── NOTICE.md
```

如果 zip 中存在多个顶层目录，或在多个目录中同时出现 `SKILL.md`，同步器会跳过该项并记录错误，避免误选入口。

## 4. URL 安全限制

内置 Skill 同步由后端在启动时主动下载远程文件，因此 URL 有严格限制。

首版只允许：

- `https://` 协议。
- host 为 `bjcdn.openstorage.cn`。
- host 为 `bjcdn.openstorage.cn` 的子域名，例如 `assets.bjcdn.openstorage.cn`。
- 默认 HTTPS 端口，或显式 `:443`。

以下 URL 会被跳过：

- `http://...`
- 非 `bjcdn.openstorage.cn` 及其子域名。
- 带 userinfo 的 URL，例如 `https://user:pass@bjcdn.openstorage.cn/file.zip`。
- 非 443 端口，例如 `https://bjcdn.openstorage.cn:8443/file.zip`。
- `localhost`、IP 地址、IPv6 literal 等 host。
- 需要 HTTP redirect 才能拿到文件的链接。

如果某一项 URL 不符合规则，SkillHub 会记录日志并跳过该项，不会阻塞应用启动。

## 5. 启动同步流程

应用 ready 后同步器会在后台执行一次，不阻塞应用 ready。

详细流程：

1. 检查 `skillhub.builtin-skills.enabled` 是否开启。
2. 读取 `classpath:builtin-skills/manifest.json`。
3. 查询 `@global` 命名空间是否存在；如果不存在，跳过同步。
4. 确保系统发布者 `builtin-skill-publisher` 存在，并且该账号带有系统账号标记。
5. 如果该用户 ID 已被非系统账号占用，直接跳过本次内置 Skill 同步，不授予 `@global` 权限。
6. 如果系统发布者还不是 `@global` 成员，则创建 `OWNER` 成员记录；已有成员记录不会自动改角色。
7. 按 manifest 顺序处理每一个 item。
8. 下载前先检查 `@global/{slug}` 和目标版本是否已经存在；如果已经确定应跳过，则不发起远程下载。
9. 只有需要发布新 Skill 或新版本时，才下载对应 zip 包。
10. 对下载到的原始 zip 字节计算 SHA-256，并与 manifest 的 `sha256` 比较；不一致时停止处理该项。
11. 解包并校验 Skill 入口 `SKILL.md`。
12. 校验 manifest 中的 `slug`、`version` 与包内元数据一致。
13. 发布前再次检查是否已存在同名 Skill 或同版本，处理并发启动场景。
14. 需要发布时调用现有 `SkillPublishService.publishFromEntries(...)`。
15. 发布完成后，该 Skill 位于 `@global/{slug}`，可见性为 `PUBLIC`。

同步逻辑不会直接写数据库 seed 数据。它复用现有发布服务，因此会保留现有的包校验、对象存储写入、版本记录、latest version 更新、事件和搜索索引同步。

## 6. 幂等与冲突处理

内置 Skill 同步支持重复启动和多次部署。

幂等键：

```text
@global/{slug} + version
```

行为说明：

| 场景 | 行为 |
|---|---|
| `@global/{slug}` 不存在 | 发布 manifest 中的 Skill |
| `@global/{slug}` 已存在，owner 是 `builtin-skill-publisher`，但目标版本不存在 | 发布新版本 |
| 同版本已存在且已发布 | 下载前跳过 |
| 同版本已存在但不是 `PUBLISHED` | 下载前跳过并记录日志 |
| `@global/{slug}` 已被其他 owner 创建或发布 | 下载前跳过并记录 warning |

这意味着内置同步不会接管用户或管理员已经创建的同 slug Skill；即使该 Skill 仍处于待审、未发布或已拒绝状态，也会跳过对应 manifest item。
同版本已存在时，同步器不会重新下载远端 zip，也不会验证远端对象内容是否发生漂移。

如果多实例同时启动，可能出现多个实例同时尝试发布同一个内置版本。同步器会在发布失败后重新查询目标版本；如果发现同版本已经以相同内容发布成功，则视为并发场景下的正常跳过。

## 7. 开关配置

内置 Skill 同步默认开启。

Spring 配置项：

```yaml
skillhub:
  builtin-skills:
    enabled: true
```

环境变量：

```dotenv
SKILLHUB_BUILTIN_SKILLS_ENABLED=true
```

如需禁用启动同步：

```dotenv
SKILLHUB_BUILTIN_SKILLS_ENABLED=false
```

禁用后，应用 ready 后不会读取 manifest，也不会下载或发布任何内置 Skill。

## 8. 维护流程

新增一个内置 Skill 的推荐步骤：

1. 将固定到上游 commit 的审查后源码加入 `builtin-skills/skills/<slug>/`。
2. 在包内保留 `LICENSE.txt`、`NOTICE.md`，在 catalog 和 evals 中登记元数据与回归用例。
3. 运行 `make test-builtin-skills`，确认包结构、来源、许可证和确定性构建门禁通过。
4. 运行 `make build-builtin-skills`，从 `builtin-skills/dist/artifacts.json` 读取制品哈希。
5. 上传 zip 到 `bjcdn.openstorage.cn` 或其子域名下的不可变路径。
6. 在 `server/skillhub-app/src/main/resources/builtin-skills/manifest.json` 中新增一项，同时填写制品 URL 和 `artifacts.json` 中对应的 SHA-256。
7. 本地或测试环境启动 SkillHub，查看后端日志确认同步结果。
8. 在 Web UI 或 API 中确认 `@global/{slug}` 已公开可见。

更新一个已有内置 Skill 的推荐步骤：

1. 不要覆盖已经发布过的旧版本 zip 内容；URL 对应对象必须保持不可变。
2. 在 `SKILL.md` 中提升 `version`。
3. 重新打包并上传新的 zip 文件。
4. 在 manifest 中新增一条同 `slug`、新 `version` 的记录。
5. 保留旧版本记录，除非产品明确不再需要该旧版本在新实例中预置。

不推荐：

- 修改旧版本 zip 内容但保持同一个 `version`。
- 把 URL 指向会发生内容变化的临时对象。
- 使用需要登录、签名跳转或重定向的下载链接。

## 9. 日志与排查

启动时可以通过后端日志观察同步结果。

常见日志含义：

| 日志含义 | 处理建议 |
|---|---|
| manifest not found | 确认 `builtin-skills/manifest.json` 是否被打进 classpath |
| publisher account id already exists but is not a system account | `builtin-skill-publisher` 已被普通账号占用；需要人工处理账号冲突后再启用内置同步 |
| slug, version, url, and sha256 are required | 检查 manifest item 是否缺字段或字段不是字符串 |
| slug is invalid | 检查 slug 是否符合 SkillHub slug 规则 |
| sha256 must be 64 lowercase hexadecimal characters | 使用 `builtin-skills/dist/artifacts.json` 中对应制品的 SHA-256 |
| URL is not allowed | 检查 URL 是否为 HTTPS、host 是否为 `bjcdn.openstorage.cn` 或其子域名 |
| package download failed | 检查云存储对象是否存在、是否返回 HTTP 200、是否超时 |
| package checksum mismatch | 云端对象与 manifest 固定的制品不一致；不要继续解包或发布，检查是否上传错误或对象被覆盖 |
| package must contain SKILL.md | 检查 zip 是否存在唯一可识别的 `SKILL.md` 入口 |
| manifest version does not match package version | 检查 manifest `version` 和 `SKILL.md version` 是否一致 |
| slug already belongs to another user | 说明 `@global/{slug}` 已被非内置发布者创建或发布，内置同步不会覆盖 |
| published fingerprint differs | 并发发布异常后发现同一内置版本已存在但内容不同，需要人工确认是否发生了版本冲突 |

如果某个 manifest item 失败，后续 item 仍会继续处理，应用可用状态不受影响。

## 10. 验收检查

配置或新增内置 Skill 后，建议至少完成以下检查：

- `make test-builtin-skills` 通过，且 15 个回归用例都有对应包。
- manifest JSON 格式合法。
- 每个 item 都包含 `slug`、`version`、`url`、`sha256`。
- 每个 `sha256` 都与 URL 下载到的原始 zip 字节一致。
- URL 使用 `https://bjcdn.openstorage.cn/...` 或可信子域名，并以 `.zip` 结尾；文件名不必等于 SHA-256。
- zip 根目录直接包含 `SKILL.md`，或只有一个顶层 Skill 目录且该目录包含 `SKILL.md`。
- `SKILL.md name` 归一化后的 slug 与 manifest `slug` 一致。
- `SKILL.md version` 与 manifest `version` 一致。
- 启动日志没有该 item 的 warning 或 error。
- Web UI 中可以看到 `@global/{slug}`。
- Skill 可被匿名或登录用户按公开 Skill 规则发现。
