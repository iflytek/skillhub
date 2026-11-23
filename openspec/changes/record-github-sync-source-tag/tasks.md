## 1. 采集服务（Go）

- [x] 1.1 `internal/repository/source_tag.go`：解析 `ls-remote --tags` 输出（顶层 semver 名、剥离提交优先、输出上限）、
  semver 优先级比较、在提交图上判定 `EXACT`/`AFTER_TAG`/`NO_TAG` 与距离（预算上限），纯函数单元测试覆盖规则与边界。
- [x] 1.2 `ResolveSourceTag`：在独立临时目录执行 `ls-remote` 与 `fetch --no-tags --filter=tree:0 <commit>`，复用
  `runBoundedGit` 的工作区预算、可用空间与取消；浅历史、超时、失败映射为 `UNKNOWN` 原因码；结束删除临时目录。
  用本地 fixture 仓库（`file://` 测试钩子）覆盖正好对应、之后 N 个、无 tag、其他分支 tag、多 tag、轻量／注释 tag、浅历史。
- [x] 1.3 `controlplane.ReportPackage` 增加可选 `SourceTag`；`worker.processRepository` 在首个需要上传的包准入后计算一次并
  复用；官方任务冷却时记 `QUOTA_COOLDOWN`，失败不影响包 outcome。worker 测试断言：判定失败时包结果与任务完成不变、
  无包上传时不调用判定、多包共用一次判定。
- [x] 1.4 `go vet ./...`、`go test ./...`、`gofmt -l` 无输出。

## 2. 后端（Java）

- [x] 2.1 Flyway `V127__github_sync_ingestion_source_tag.sql`：`source_tag_json JSONB` 可空列与对象类型约束。
- [x] 2.2 `PackageReportRequest` 增加可选 `sourceTag`；`GithubSyncUploadAppService` 校验并规范化
  （不合法 → `UNKNOWN/INVALID_REPORT`）；`acceptReport` 插入时写入，幂等重放不更新。单元测试覆盖合法、不合法、缺省。
- [x] 2.3 `GithubSkillSourceResponse` 增加 `upstreamTag`、`declaredVersion`；`GithubSkillSourceQueryRepository` 读出
  `source_tag_json` 与版本 parsed metadata 的 `upstreamVersion`，新增按版本 ID 查询（不要求是当前发布版本，仍要求
  `PUBLISHED`）；空列返回 `UNRECORDED`。
- [x] 2.4 `SkillVersionDetailResponse`、`CliResolveResponse` 增加可选 `githubSource`；`SkillController#getVersionDetail`
  与 `CliSkillAppService#resolve` 在既有授权之后填充。Controller 测试覆盖同步版本有、非同步版本无。
- [x] 2.5 Postgres 集成测试：回报带来源 tag → 摄入列落库 → 发布后详情、版本详情、CLI resolve 返回一致；旧摄入为
  `UNRECORDED`；重放回报不改写。
- [x] 2.6 `make test-backend-app`（或含 `-am` 的等价 Maven）。

## 3. 生成类型与 Web

- [x] 3.1 `./scripts/check-openapi-generated.sh` 刷新 `web/src/api/generated/schema.d.ts`，不手改。
- [x] 3.2 `web/src/api/types.ts` 的 `GithubSkillSource` 增加 `upstreamTag`、`declaredVersion`；来源卡片新增上游版本与
  声明版本两行，标注仓库 tag；en/zh/ko/ru 文案。组件测试覆盖五种状态与单仓多 Skill 声明版本。
- [x] 3.3 `pnpm typecheck`、`pnpm lint`、`pnpm test`、`pnpm build`（在 `web/` 下运行）。

## 4. CLI

- [x] 4.1 `ResolveResponse` 增加可选 `githubSource`；`outdated` 人类输出附 `upstream <tag>[+N]`、JSON 附 `remoteSource`；
  `install` 成功行附同样信息；无来源时输出不变。集成测试用 fake registry 覆盖有／无来源。
- [x] 4.2 `make lint-cli`、`make typecheck-cli`、`make test-cli`、`make build-cli`。

## 5. 收尾

- [x] 5.1 `openspec validate record-github-sync-source-tag --strict`。
- [x] 5.2 用两个真实公开仓库实测判定：`obra/superpowers@2ce8aa15` → `EXACT v7.0.1`；
  `mattpocock/skills@4ae2317d` → `AFTER_TAG v1.3.2 +3`（显式开关的 live 测试，默认跳过）。
