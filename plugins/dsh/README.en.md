# SkillHub for DeepSeek Harness

[中文](README.md)

A native DSH plugin for anonymous skill browsing, conversation search and installation, and local upgrade/removal. Open **技能中心** (Skill Center) in the DSH sidebar. The installation core is bundled; a separate SkillHub CLI installation is unnecessary.

Version `0.2.0` adds source-aware installation status to catalog cards and conversation results, including recorded versions, user/project scopes and abnormal records. Conversation status is a query-time snapshot. No matching record does not exclude manual or other-source installations, and file presence does not prove DSH activation. Matching uses Registry source, namespace and slug; a same-named directory from another source is not claimed.

Version `0.2.1` defaults to `https://skill.xfyun.cn` when `registryUrl` is omitted. An explicit empty or whitespace-only value still disconnects; existing custom addresses are preserved. CLI defaults and credentials are not inherited.

Version `0.4.0` uses the independent `dsh-v0.4.0` tag. It adds versioned examples and native composer handoff, conversational installation management, and per-location installed versions while retaining the dual-RC compatibility of `0.3.1`. Check npm for public availability; locally built tarballs can be verified independently as described below.

`0.5.0` adds read-only skill availability diagnosis in installed cards and conversations, with separate integrity, discovery and invocation-policy results. It retains the compatibility and inventory format of `0.4.0`.

## Use an installed skill

Choose “使用此技能” on an installed card, matching installed-version detail or completed installation result. Catalog entries open a new conversation in the same workspace; conversation entries keep their current session. After installation verification, examples appear above the native composer. Confirm an example or choose a custom task to insert an editable invocation. The original conversation is preserved, and in-conversation use retains its existing text, references and attachments. Complete the task and send it yourself; this action does not submit messages, call a model or install files.

An active session is required. Shadowed skills, modified files, disabled user invocation or changed context are reported without modifying the draft. Historical receipts are not proof of current installation. This feature is available from `0.4.0`.


## Requirements and validation scope

| Component | Requirement or scope |
| --- | --- |
| DSH | `0.2.0-rc.2` or `0.2.1-rc.2`; Host, app-boot and injected Client modules must share the exact release |
| Cordis | Old RC: both families below; `0.2.1-rc.2`: only `4.0.5 / 1.0.6 / 1.0.10 / 1.1.7` |
| Host Node | `>=24.2.0 <25`; Node 24.0.0 is unsuitable |
| Build package manager | Bun `1.3.13`, using frozen lockfiles |
| UI | Chinese, DSH light/dark themes and narrow layouts |
| Verified environments | Both Linux DSH Hosts: strict installation, native probes and browser user/project install, upgrade, removal and cancellation; Windows browser UI and interactions against a real Host |
| Validation boundary | The feature implementation has real-model and cross-platform CI evidence; built artifacts and public packages have separate acceptance records. These results do not substitute for desktop clients, native Windows/macOS manual interactions or SaaS acceptance |

Windows browser acceptance does not establish complete native Windows Host filesystem support. CI results do not replace desktop, model, or target-instance acceptance. See the repository's [compatibility design](../../openspec/changes/add-dsh-native-plugin/compatibility-design.md) and [acceptance criteria](../../openspec/changes/add-dsh-native-plugin/acceptance.md).

Version `0.3.1` includes the merged [Issue #981 compatibility fix](https://github.com/iflytek/skillhub/pull/988). Published plugin `0.3.0` does not include it. Unknown releases and mixed Host/Client graphs reject new writes. Optional `userQuestions` is enabled by Host service availability; it is not part of the mandatory write-version gate. Its API is identical in the two enumerated RCs, both of which the peer declaration permits. An absent service falls back to the plugin confirmation dialog.

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

`dsh-typert-generator` remains pinned to `0.2.0-rc.2` to emit the common RPC contract for both Hosts; other DSH development modules use `0.2.1-rc.2` for type checking. This compiler pin does not bypass the runtime release guard.

`check` builds Host/Client, generated Remote bindings and declarations, then runs type checking, lint and tests. Lint uses the locked tools in `cli/`. Bun is a build dependency; the packaged Host runs under Node. React and React DOM are supplied by DSH.

Create a separate output directory outside the checkout, then generate the candidate:

```sh
npm pack --ignore-scripts --pack-destination /absolute/path/to/candidate-output
```

A dry run does not produce an installable package. Retain each candidate and its checksum; do not overwrite packages used for acceptance or rollback.

This version retains the following complete combinations from `0.1.1`, rejecting mixed or unlisted patch versions. Pinning the DSH main package does not lock its transitive `~` dependencies; the actual Host resolution is checked.

| Combination | Cordis | Loader | Include | Timer |
| --- | --- | --- | --- | --- |
| Retained baseline | `4.0.4` | `1.0.5` | `1.0.9` | `1.1.6` |
| New patch combination | `4.0.5` | `1.0.6` | `1.0.10` | `1.1.7` |

Published `0.1.0` still requires the original Cordis `4.0.4` baseline and does not include these fixes. Historical receipts use a freshly verified read-only context; expired installation references and plans never regain write authorization.

## Install from npm

Query the exact npm version first and install only after confirming availability. Check DSH, Node and a complete Cordis combination listed above, stop the target Web Profile Host, then install the plugin. A verified local tarball can also be installed using the next section:

```sh
npm view @astron-team/skillhub-dsh@0.5.0 version
dsh plugin --profile skillhub-test add @astron-team/skillhub-dsh@0.5.0 --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

Replace `skillhub-test` with the intended Web Profile; initial creation is documented below. When `registryUrl` is omitted, the plugin uses `https://skill.xfyun.cn`. To use a self-hosted instance, change, test and save the source in plugin settings. Explicitly saving an empty address disconnects. Diagnose npm query/download failures separately from plugin loading errors.

## Upgrade from an older version

`0.4.0` uses inventory schema v2 with per-location versions. The installation core remains bundled, so a separate CLI is unnecessary. If the same user also manages these skills through SkillHub CLI, upgrade every such CLI to `0.2.0` before upgrading the plugin and stop scheduled/background writes from old clients. Confirm availability with `npm view @astron-team/skillhub@0.2.0 version`, then run `npm install -g @astron-team/skillhub@0.2.0`, verify `skillhub version`, then install the exact plugin version above.

Reading v1 inventory does not migrate it. Before the first v2 write, a byte-verified `inventory.v1.<sha256>.backup.json` is retained beside the inventory. User and project copies may retain different versions. Old CLI/v2 inventory mixed writes are unsupported. Once locations have changed independently, restoring the old inventory or downgrading the plugin alone is not a rollback: skill files, installation metadata and operation records must also be reconciled.

Authors provide examples at the fixed `.skillhub/examples.json` path inside each skill package. The file keeps its location after installation and follows the skill's version and fingerprint; no separate global example copy is created. Skills without examples use a custom task; invalid declarations remain ordinary files without exposing invalid examples.

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
| `registryUrl` | Registry base URL; defaults to `https://skill.xfyun.cn` when omitted; explicit empty/whitespace disconnects without fallback; custom values are preserved |
| `detailTabs.versions` | Version history visibility |
| `detailTabs.securityAudit` | Security audit visibility |
| `detailTabs.reviews` | Read-only review visibility |

Each visibility setting accepts `auto`, `show` or `hide`. Auto detects actual anonymous availability. Show does not grant server access. Hide does not request the module. Anonymous audit access is currently denied; this must not be presented as a successful audit. A SaaS instance without reviews can hide that tab.

## Browse, install and manage skills

- Browse supports search, server sorting, real labels and explicit **Load more**, normally 12 results per page. A first-page 404/405 can fall back to bounded legacy search, with an explicit pagination/sorting limitation. A failed label filter never silently returns unfiltered results.
- Details show fixed-version documentation and optional tabs. Select a downloadable version before confirming installation. Existing-target protection remains; skill downgrades are not supported.
- Installed records can be filtered by source, scope and keyword. The right-side version card contains detail refresh and update checks. Upgrade and removal affect only the selected location; other locations retain their versions. Missing directories have a separate stale-record cleanup action.
- `astron_skillhub_search` only searches. Result cards provide a detail action; the drawer offers explicit installation confirmation.
- An explicit request to install a uniquely selected result does not require opening the drawer first. `astron_skillhub_install` verifies the search-time version and content snapshot. It accepts a `selectionRef` or a full coordinate, exclusively, and a `user`/`project` scope. Expired or ambiguous references are rejected without switching to current latest.

Local operations require an active conversation so that the Host can resolve trusted roots. Files are written on the Host machine, not the browser machine. There is no login, review-submission or permission-management flow.

Acceptance, completed file writes, DSH discovery and model use are distinct states. Expand **安装记录** to inspect verification, current inventory and the original operation. A historical completion does not prove that version is still installed or has been executed. After a Host restart, historical searches require explicit **重新查看详情** to revalidate the record; old references do not regain write permission.

## Verify a candidate

From `plugins/dsh`:

```sh
node scripts/smoke-native-host.mjs --artifact /absolute/path/to/candidate.tgz --report /absolute/path/to/host-smoke-summary.json --dsh-version 0.2.1-rc.2
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

The plugin Host calls anonymous Registry HTTP APIs; local file transactions stay on the Host. SaaS does not need a new API that controls user filesystems. Define and verify contracts in open source first, then synchronize missing routes, fields, anonymous policy and tests to SaaS. Public npm `0.2.0` has verified catalog, search, documentation, version selection and fixed-version project installation against `https://skill.xfyun.cn`, including files/fingerprint, inventory and DSH discovery. Each version’s built artifacts and public package require separate acceptance. Non-SemVer versions with no reliable ordering, including date-based versions, remain blocked from automatic upgrades; no executable upgrade action is offered. Verified first-time fixed-version installation remains available. Real model execution of SaaS skills is unverified. See the [Registry contract](../../openspec/changes/add-dsh-native-plugin/registry-api-contract.md).

The plugin uses independent `dsh-v<version>` tags. `check-dsh-plugin.yml` runs normal clean-install checks on Linux/macOS/Windows × Node 24.2.0/current Node 24, plus an isolated retained-Cordis baseline regression. `release-dsh-plugin.yml` is manual and defaults to candidate-only operation. Publishing requires an approved version, publishable manifest, candidate acceptance and explicit release authorization; configured environment protection rules must also be satisfied. Version updates are explicit commits; npm publication uses the dedicated workflow after release authorization.

See the [release guide](../../openspec/changes/add-dsh-native-plugin/release-guide.md) and [release notes](../../openspec/changes/add-dsh-native-plugin/release-notes.md). OpenSpec and integration links refer to the source repository; those documents are not included in the tarball.

### Marketplace interaction and installation management

Market cards open details and offer separate installation-management actions. Multiple installed scopes require a selected target. After an explicit install request, an omitted scope defaults to current user; project scope must be explicitly selected. Market install actions open the same dialog without detail-page navigation. Current user is preselected, with project scope in advanced options; the dialog never submits automatically. A later explicit user scope may start a new call. Details omit complete leading frontmatter and reuse public reads for 60 seconds without bypassing write validation. The versions tab requests `downloadableOnly=true`, requiring the updated open-source backend; SaaS synchronization and acceptance remain separate.

After confirmation, the installation dialog expands to the right, keeping the skill, version, and scope on the left and concise real progress on the right. Narrow screens stack the sections. Progress remains indeterminate until completion; closing the dialog does not cancel a submitted installation.

Installation controls use current inventory rather than historical success receipts. An existing installation selects its scope and offers removal. Project-only scope uses the project directory; all-project scope uses the current user directory on this Host. Changing scope never installs automatically; a removed installation may be explicitly installed again.

Removal from market cards, installed rows, and detail controls opens an in-place confirmation. It shows the skill, version, selected scope, and impact. Opening only prepares a plan; confirmation executes it. Cancel and Escape never remove files, and existing expiry, identity, and local-file checks still apply.

Version selection is read-only. A verifiable newer selected version offers an explicit exact-version upgrade; equal versions do not upgrade again, older versions cannot downgrade, and unorderable versions are blocked. Confirmation lists only the selected installation location. Latest-update checks remain available, with in-place confirmation, local-change protection, and recovery.


### Conversation management

`astron_skillhub_list_installed` lists current-source managed records in the current user/project roots, not every skill on the machine. `astron_skillhub_check_update` checks the latest or an explicitly requested newer version. `astron_skillhub_upgrade` and `astron_skillhub_uninstall` require an exact installed reference and real human confirmation, including explicitly selected scopes. Without a native question provider they return a confirmation card. Models cannot authorize operations using paths, plan IDs or confirmation flags. `astron_skillhub_operation` reads the original session-owned receipt without resubmitting a mutation.

New conversational installs default to user scope after an explicit install request. Project installation is explicit/advanced. Updating one location preserves other locations' versions. Local edits and stale references block mutations. CLI installations sharing this inventory must be upgraded together; old CLI/new inventory mixed writes are unsupported.


### Skill availability diagnosis (0.5.0)

Choose “更多 → 检查可用性” on a conversational installed-skill card to inspect its selected location. A conversation can also use the read-only `astron_skillhub_check_availability` tool to explain why a skill cannot be used.

The result distinguishes local integrity, current-session discovery, and user/model invocation policies. Shadowing, local modifications and unknown Host state are separate outcomes. A name-only lookup reports Host observations without proving installation ownership or integrity. Diagnosis never modifies, removes or enables/disables a skill, edits a draft, or sends a message. A historical result is not a guarantee about a later invocation.
