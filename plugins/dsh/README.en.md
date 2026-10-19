# SkillHub for DeepSeek Harness

[中文](README.md)

A native DSH plugin for anonymous skill browsing, conversation search and installation, and local upgrade/removal. Open **技能中心** (Skill Center) in the DSH sidebar. The installation core is bundled; a separate SkillHub CLI installation is unnecessary.

`@astron-team/skillhub-dsh@0.1.0` is published on npm. This branch contains compatibility and historical installation receipt fixes that have not been released yet; the next version is selected separately. Validate these changes with a tarball built from this branch, not the published `0.1.0` package.

## Requirements and validation scope

| Component | Requirement or scope |
| --- | --- |
| DSH | `0.2.0-rc.2`, with the matching official module versions |
| Cordis | The two explicitly verified complete combinations below |
| Host Node | `>=24.2.0 <25`; Node 24.0.0 is unsuitable |
| Build package manager | Bun `1.3.13`, using frozen lockfiles |
| UI | Chinese, DSH light/dark themes and narrow layouts |
| Verified environments | Linux DSH Host/browser integration; Windows browser UI and interactions against a real Host |
| Pending validation | Real macOS Host/client; other DSH releases; SaaS instances |

Windows browser acceptance does not establish complete native Windows Host filesystem support. CI results do not replace desktop, model, or target-instance acceptance. See the repository's [compatibility design](../../openspec/changes/add-dsh-native-plugin/compatibility-design.md) and [acceptance criteria](../../openspec/changes/add-dsh-native-plugin/acceptance.md).

## Build and package

From the repository root:

```sh
cd cli
bun install --frozen-lockfile
cd ../plugins/dsh
bun install --frozen-lockfile
bun run check
npm pack --dry-run --ignore-scripts
```

`check` builds Host/Client, generated Remote bindings and declarations, then runs type checking, lint and tests. Lint uses the locked tools in `cli/`. Bun is a build dependency; the packaged Host runs under Node. React and React DOM are supplied by DSH.

Create a separate output directory outside the checkout, then generate the candidate:

```sh
npm pack --ignore-scripts --pack-destination /absolute/path/to/candidate-output
```

A dry run does not produce an installable package. Retain each candidate and its checksum; do not overwrite packages used for acceptance or rollback.

This branch verifies the following complete combinations, rejecting mixed or unlisted patch versions. Pinning the DSH main package does not lock its transitive `~` dependencies; the actual Host resolution is checked.

| Combination | Cordis | Loader | Include | Timer |
| --- | --- | --- | --- | --- |
| Retained baseline | `4.0.4` | `1.0.5` | `1.0.9` | `1.1.6` |
| New patch combination | `4.0.5` | `1.0.6` | `1.0.10` | `1.1.7` |

Published `0.1.0` still requires the original Cordis `4.0.4` baseline and does not include these fixes. Historical receipts use a freshly verified read-only context; expired installation references and plans never regain write authorization.

## Install from npm

These commands install published `0.1.0`. Check DSH, Node and that version's original Cordis baseline, stop the target Web Profile Host, then install the exact version. Until the branch fixes are released, use the local candidate instructions below to validate them:

```sh
dsh plugin --profile skillhub-test add @astron-team/skillhub-dsh@0.1.0 --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

Replace `skillhub-test` with the intended Web Profile; initial creation is documented below. The plugin has no default Registry URL: enter a source in plugin settings, test it and save it. Before publication, use a local candidate; failure to fetch an unpublished npm version does not indicate a plugin loading error.

## Install a local candidate

These commands target the specified DSH Web release. Use the same Profile and DSH home for installation, startup and maintenance. `skillhub-test` is an example Profile. Skill-write testing requires a dedicated test account/container, workspace and test skills: a separate Profile or `DSH_HOME` alone does not isolate the shared inventory.

Create a new Web Profile once:

```sh
dsh --profile skillhub-test --from-default-profile web --dump-config
```

Stop the Host using that Profile, install the tarball, and start it:

```sh
dsh plugin --profile skillhub-test add /absolute/path/to/candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

Open the local entry printed by the Host. It may contain an access credential; do not include it in reports, screenshots or commits. The example binds loopback. Remote access requires the environment's existing access controls.

Skip Profile creation for an existing Web Profile. The base Profile automatically created by `plugin add` does not supply the Web Client. If `dsh` is not on PATH, replace it with `node /absolute/path/to/@deepseek-ai/dsh/lib/bin.js` from the installed target release.

The Loader entry ID is `astron-skillhub`; `skillhub` is the Host/Remote service name. Custom patches must target the owning plugin's correct Loader ID, without changing another plugin's entry.

## Configure a Registry

Open DSH **Plugins → @astron-team/skillhub-dsh → SkillHub 设置**.

1. Enter the HTTP(S) instance base address in **Registry 地址**. A deployment subpath is allowed; credentials, query parameters, fragments and API endpoint suffixes are not.
2. Select **测试连接** (Test connection) to test the current draft URL without saving it. The result reports public catalog or basic-search availability and elapsed time. Requests have a 10-second timeout; failures can be retried. Editing the address clears the previous result. Success does not establish download or all optional-module availability.
3. Choose visibility for versions, security audit and reviews.
4. Select **保存配置** (Save). After the saved confirmation, open **技能中心** and search for a public test skill.

**已配置** means a nonempty address is saved, not that connectivity has been tested. An empty address disconnects the source. **重置修改** (Reset changes) restores the current Host snapshot without a network reload or save. Read-only connections cannot edit configuration. On a revision conflict, reset the draft before editing the current values.

| Field | Meaning |
| --- | --- |
| `registryUrl` | Registry base URL; empty means no default external source is contacted |
| `detailTabs.versions` | Version history visibility |
| `detailTabs.securityAudit` | Security audit visibility |
| `detailTabs.reviews` | Read-only review visibility |

Each visibility setting accepts `auto`, `show` or `hide`. Auto detects actual anonymous availability. Show does not grant server access. Hide does not request the module. Anonymous audit access is currently denied; this must not be presented as a successful audit. A SaaS instance without reviews can hide that tab.

## Browse, install and manage skills

- Browse supports search, server sorting, real labels and explicit **Load more**, normally 12 results per page. A first-page 404/405 can fall back to bounded legacy search, with an explicit pagination/sorting limitation. A failed label filter never silently returns unfiltered results.
- Details show fixed-version documentation and optional tabs. Select a downloadable version before confirming installation. Existing-target protection remains; skill downgrades are not supported.
- Installed records can be filtered by source, scope and keyword. The right-side version card contains detail refresh and update checks. Upgrade confirms the complete target set; removal affects one confirmed location. Missing directories have a separate stale-record cleanup action.
- `astron_skillhub_search` only searches. Result cards provide a detail action; the drawer offers explicit installation confirmation.
- An explicit request to install a uniquely selected result does not require opening the drawer first. `astron_skillhub_install` verifies the search-time version and content snapshot. It accepts a `selectionRef` or a full coordinate, exclusively, and a `user`/`project` scope. Expired or ambiguous references are rejected without switching to current latest.

Local operations require an active conversation so that the Host can resolve trusted roots. Files are written on the Host machine, not the browser machine. There is no login, review-submission or permission-management flow.

Acceptance, completed file writes, DSH discovery and model use are distinct states. Expand **安装记录** to inspect verification, current inventory and the original operation. A historical completion does not prove that version is still installed or has been executed. After a Host restart, historical searches require explicit **重新查看详情** to revalidate the record; old references do not regain write permission.

## Verify a candidate

From `plugins/dsh`:

```sh
node scripts/smoke-native-host.mjs --artifact /absolute/path/to/candidate.tgz --report /absolute/path/to/host-smoke-summary.json
```

This uses an isolated Web Profile to test installation and Host loading. It makes no model calls and does not replace UI or full skill lifecycle acceptance. Source-only browser and transaction probes are documented in the [integration guide](test/integration/README.md).

Check an anonymously downloadable, published test skill at an exact version:

```sh
bun run probe:registry --registry 'https://registry.example' --namespace 'demo' --slug 'test-skill' --version '1.0.0' --output /absolute/path/to/registry-report.json
```

Replace example values and use a new output file. The probe checks search, resolution, metadata, documentation, optional modules and archive fingerprints. Extraction is temporary; it does not install or execute skills. Directory pagination, labels, sorting and actual DSH pages require separate tests. Required failures return a nonzero exit code; audit HTTP success does not establish a validated public audit schema.

Manual checks should include configuration save/reset, search/details, user/project installation, upgrades/removal, history, themes/narrow layouts, failure retries and source changes. Model acceptance requires separate configuration and call-budget authorization.

## Update, rollback and refresh

1. Stop new operations and finish or recover accepted transactions in the original compatible environment.
2. Retain the current tarball and stop the Host using the Profile.
3. Install the replacement with the same `plugin ... add` command. Preserve the Profile, Registry, conversations and model settings.
4. Restart the Host and open its valid entry. If the browser still shows old UI, hard-refresh it (Windows: Ctrl+Shift+R).
5. Check Skill Center, configuration, existing records and historical queries before exercising a test skill.

Browser refresh does not reload Node modules. Dependency changes require a full Host restart; disabling/re-enabling the plugin is insufficient. Official RC.2 may leave the original browser tab blank after a same-port Host restart. Re-enter using a valid entry and preserve operation records; do not resubmit installation to recover the UI.

Rollback uses a retained, verified compatible tarball and the same procedure. Replacement between development candidates is not a promise of cross-release journal compatibility. Never delete journals or replace the package to bypass pending recovery. Plugin rollback is separate from skill-version downgrade.

Disable/re-enable the package using its switch in DSH Plugins. To remove it:

```sh
dsh plugin --profile skillhub-test remove @astron-team/skillhub-dsh
```

`remove` does not accept `--ignore-scripts`. Package removal does not uninstall skills or delete inventory and operation logs. For an uncertain result, query the original request; use **核验并恢复** when recovery is required.

## Server synchronization and independent release

The plugin Host calls anonymous Registry HTTP APIs; local file transactions stay on the Host. SaaS does not need a new API that controls user filesystems. Define and verify contracts in open source first, then synchronize missing routes, fields, anonymous policy and tests to SaaS. SaaS acceptance remains pending. See the [Registry contract](../../openspec/changes/add-dsh-native-plugin/registry-api-contract.md).

The plugin uses independent `dsh-v<version>` tags. `check-dsh-plugin.yml` runs normal clean-install checks on Linux/macOS/Windows × Node 24.2.0/current Node 24, plus an isolated retained-Cordis baseline regression. `release-dsh-plugin.yml` is manual and defaults to candidate-only operation. Publishing requires an approved version, publishable manifest, candidate acceptance and explicit release authorization; configured environment protection rules must also be satisfied. This branch does not bump or publish the package automatically.

See the [release guide](../../openspec/changes/add-dsh-native-plugin/release-guide.md) and [release notes](../../openspec/changes/add-dsh-native-plugin/release-notes.md). OpenSpec and integration links refer to the source repository; those documents are not included in the tarball.
