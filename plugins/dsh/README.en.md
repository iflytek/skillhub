# SkillHub for DeepSeek Harness

The plugin Loader entry ID is `astron-skillhub`; use this ID in handwritten patches. `skillhub` is the Host/Remote service name, not the Loader ID. Host and browser registration coexistence with `@cocofhu/skillhub@0.2.16` passed in isolated RC.2 tests; this does not cover all reference-plugin features or model tool selection. Inspect the owning package before updating any old development patch targeting `id: skillhub`; do not change the reference plugin entry.

The candidate Node range is `>=24.2.0 <25`, enforced by package engines and the Host write guard. Linux checks cover Node 24.2.0 and 24.17.0; Windows has separate runtime evidence on Node 24.16.0. The complete platform matrix still requires validation. Node 24.0.0 lacks the import.meta.main property used by the candidate DSH CLI and exits without starting the Host.


[中文](README.md)

A native DSH plugin for anonymous SkillHub browsing, conversation search and installation, and local skill management. The plugin runs inside DSH; installing the SkillHub CLI separately is unnecessary.

This is a **private development package**, currently `@astron-team/skillhub-dsh@0.0.0-m0`. It is not an npm release or a declaration of production support. Tests target DSH `0.2.0-rc.2` in isolated Web profiles. Linux and Windows have individual runtime evidence, and anonymous API/native installation checks passed against an open-source test Registry. Real-model search, explicit installation, ambiguous-selection clarification, resource reading, Stop boundaries and browser reconnection have evidence. The complete acceptance matrix and remote Host validation remain incomplete; macOS validation is deferred. The interface currently uses Chinese text; this English guide does not imply an English UI.

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

`check` builds Host/Client and the generated Remote bindings, then runs type checking, lint, and tests. The package manager baseline is in `package.json`. Bun is a build dependency; the packaged Host runs under Node. The shared installation core is bundled into `lib/local-core.js`, with no CLI command entry point or credential-store dependency.

To create a candidate tarball, replace the output path with an existing empty directory:

```sh
npm pack --ignore-scripts --pack-destination /absolute/path/to/candidate-output
```

Keep each candidate separately. Do not replace a tarball that is already being used as verification evidence.

## Install a candidate in an isolated DSH profile

The following commands use the CLI interface of the candidate DSH release. Set `DSH_HOME` to your isolated test directory and use a test profile. `DSH_HOME` must remain the same across installation and Host startup. Test skill operations also require an isolated operating-system home and workspace; changing `DSH_HOME` alone does not isolate the shared SkillHub inventory.

```sh
(
probe_root="$(mktemp -d -t skillhub-dsh-XXXXXX)"
mkdir -p "$probe_root/home" "$probe_root/agents" "$probe_root/workspace"
export HOME="$probe_root/home"
export USERPROFILE="$probe_root/home"
export DSH_HOME="$probe_root/dsh"
export DSH_AGENTS_HOME="$probe_root/agents"
cd "$probe_root/workspace"
dsh --profile skillhub-test --from-default-profile web --dump-config
dsh plugin --profile skillhub-test add /absolute/path/to/candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
)
```

These are POSIX shell commands. Resolve the DSH executable before isolating HOME, since a tool manager may otherwise lose its installation. Use `--from-default-profile web` only to create a new profile; skip it for an existing Web profile. A base profile initialized by `plugin add` does not provide the Web Client.

If DSH is not on `PATH`, replace `dsh` with `node /absolute/path/to/@deepseek-ai/dsh/lib/bin.js` from the intended DSH installation. The plugin command forwards arguments to pnpm and can initialize a missing profile, **including when requesting pnpm help**. Set the isolated environment before invoking it. These instructions cover the Web profile tested here; Desktop distribution and profile-management commands require separate validation.

The package name must not collide with another plugin's tools or views. This plugin registers `astron_skillhub_search` and `astron_skillhub_install`; it does not replace an existing registration.

## Replace or roll back a development package

Stop new operations, wait for accepted work to finish, and resolve pending recovery in the original environment. Retain the current tarball and stop the DSH Host before replacing the package in the same profile:

```sh
dsh plugin --profile skillhub-test add /absolute/path/to/target-candidate.tgz --ignore-scripts
dsh --profile skillhub-test --host 127.0.0.1 --port 0 --no-open
```

To roll back, use the retained compatible tarball and restart the Host. Preserve skill files, inventory and operation logs. Do not overwrite archived packages. If a custom patch targets the earlier development Loader ID `skillhub`, update it to the target package's ID (`astron-skillhub` in the current package) without modifying the reference plugin entry.

Replacement and rollback between two distinct `0.0.0-m0` development artifacts preserved completed records and user/project skills. A separate test also verified explicit recovery of a pending dual-target transaction after rolling back between these development artifacts. Neither test establishes compatibility between different published versions or journal-format migration. Unknown journal formats block local operations; deleting logs is not a recovery procedure.

## After changing the DSH runtime

1. Stop submitting new skill operations and wait for accepted operations to finish. Resolve pending recovery in the original compatible environment and retain operation logs.
2. Fully stop and restart the DSH Host using this profile after changing its dependencies. Toggling SkillHub or refreshing the browser does not clear loaded Node modules.
3. Reconnect and recheck compatibility. This development package accepts only its pinned candidate dependency combination. A working page on another DSH version does not establish support for install, upgrade, removal, or recovery writes.
4. If verification fails, retain inventory and operation logs. Use the matching DSH distribution or a plugin validated for the new runtime. Do not upgrade individual host packages or edit version fields to bypass verification.

The compatibility retry only checks current dependencies; it does not install packages, restart the Host, or clear module caches. Full cross-release upgrade and rollback acceptance remains incomplete.

DSH may reject incompatible peers before loading the plugin, so SkillHub's own compatibility UI may not appear. A version exemption does not establish support. If the installer reports an incomplete profile rollback, follow its profile dependency repair instructions; do not delete skill directories or operation logs. Installation rejection and subsequent `plugin install` repair were verified in an isolated Alpha.3 environment; Alpha.3 is not a supported runtime for this package.

## Connect a Registry

Open the SkillHub plugin configuration in DSH and set:

| Field | Meaning |
| --- | --- |
| `registryUrl` | HTTP(S) Registry base URL, including any deployment subpath. An empty value leaves the plugin unconfigured. Do not include credentials, query parameters, or fragments. |
| `detailTabs.versions` | Version-history visibility: `auto`, `show`, or `hide`. |
| `detailTabs.securityAudit` | Security-audit visibility using the same modes. The current backend rejects anonymous audit access. |
| `detailTabs.reviews` | Read-only review visibility using the same modes. |

`auto` displays a module after its availability has been confirmed. `show` retains its entry and reports read failures. `hide` does not request that module. A transient failure is not proof that a feature is unsupported; use the explicit retry control. Configuration changes invalidate unaccepted references from the previous source.

All Registry reads are anonymous. There is no sign-in, token setup, review submission, or permission-management flow. JSON and documentation reads have a 1 MiB limit and a 30-second deadline covering headers and body. Archive downloads use the shared installation core and its own bounds.

## Browse and manage skills

- **浏览技能**: search, open a fixed-version detail page, and choose an available user or project installation scope. Return navigation preserves the list context.
- **已安装**: filter local records by coordinate/version and scope. Filtering does not query the Registry. Open details, explicitly check for updates, or enter upgrade/removal confirmation.
- Version, audit, and review tabs follow the configured visibility. HTML is not executed and remote Markdown images are not loaded.
- File installation and DSH discovery are reported separately. Neither means that a model has executed the skill.

A DSH conversation is required for local operations so that the Host can resolve the actual agent and workspace. The browser and model cannot supply arbitrary filesystem targets. With a remote DSH Host, files are installed on that Host, not on the browser's machine.

Update checks run only after a user action. The row and detail view share the check result. Upgrade preparation verifies the complete affected target set and creates a plan valid for five minutes. Review all locations before confirming. This release does not offer automatic updates, skill downgrades, or force overwrite.

Removal affects one confirmed installation target. If its directory is missing, the separate stale-record action removes only the record. Other locations and the remote Registry skill remain unchanged. Running tasks are not forcibly terminated.

## Disable and re-enable the plugin

Finish or recover accepted skill operations first. In DSH **Plugins**, select `@astron-team/skillhub-dsh` and turn off its enable switch. Wait until the switch is off and the SkillHub entry disappears. Disabling does not uninstall skills or delete inventory and operation logs.

Turn the same switch on and wait for the SkillHub entry to return. Two disable/enable cycles were verified in an isolated RC.2 Host and browser, preserving the reference plugin's tools and views. Changes to Host dependencies still require a full restart. Installed skills may remain discoverable while the plugin is disabled; use precise removal in the installed-skills page to remove a skill.

## Use SkillHub in a conversation

The search tool returns native result cards. Open a card's detail drawer to inspect the fixed version and install it explicitly. The install tool is intended for an explicit user installation request; search alone does not install anything.

Installation acceptance is not completion. The result card queries the Host operation and shows its actual outcome. Historical cards do not reinstall skills. Upgrade and removal are performed in the management page, not by conversation tools in this release.

Native tool execution, real-model search and installation, ambiguous-selection clarification, resource use, Stop boundaries, browser reconnection, and stale-record cleanup from real installation history have been tested with a local HTTP fixture. Anonymous APIs and native installation have separate open-source Registry evidence. A real model also loaded the previously installed Registry skill zero-slop and read its resource; the successful prompt supplied the absolute resource path, so it does not prove reliable relative-path resolution for arbitrary skills. See the OpenSpec verification record for the evidence and remaining delivery gates.

After a Host restart, old search references expire. Open the historical card and explicitly select “重新查看详情” (Reopen details). The Host validates the original search in the current session, checks its source and fixed version, and issues fresh detail references. The drawer shows both sources. This does not install a skill or restore the old reference. Search again if the history, source or session cannot be verified.

## Uncertain results and recovery

After a Host restart, unfinished startup operations are shown as uncertain so they can be verified and recovered; the Host does not replay them or rewrite their journals on startup. Execution locks and current ownership checks still protect recovery. When a response is lost, query the original request instead of submitting a new operation. The client saves the request before submission and retains independent upgrade/removal history even when the installed row disappears. This supports refresh in the same browser tab; recovery after closing the browser is not guaranteed by sessionStorage.

For a pending recovery or cleanup warning, use **核验并恢复**. The Host validates the original operation and current target ownership before changing files: restore pre-commit files, or clean up residues after a committed operation. It does not repeat the installation/removal. Unresolved results cannot simply be dismissed. Retain the operation ID when reporting a failure; do not delete journals or unknown directories to bypass recovery checks.

The runtime guard checks DSH, app-boot and Host peers through the Host package resolver, plus owning manifests of the declared Client modules, including the session UI module, in the active graph. Missing services, inactive modules or version mismatches block new writes and recovery writes. Reading existing operation results remains available. Matching dependencies is only one compatibility check and does not establish support for every DSH/Client/Node/platform combination.

Known Host limitation: official RC.2 can show a blank page when the Host restarts on the same port while the browser stays open. This has also been reproduced without SkillHub and still affects plugin use. Automatic reconnection is not guaranteed. Keep the original tab and operation records; do not resubmit an installation or upgrade. See the [compatibility design](../../openspec/changes/add-dsh-native-plugin/compatibility-design.md) for the separate re-entry recovery acceptance boundary.

## Plugin maintenance

Stop new skill operations and resolve pending transactions before replacing or removing the plugin. Install a replacement candidate with the same `plugin ... add` command, then restart the test Host and verify that Host and Client load together.

To remove the package from a test profile:

```sh
dsh plugin --profile skillhub-test remove @astron-team/skillhub-dsh
```

This manages the profile package, not the skill inventory. Removal and reinstallation of a retained candidate have been tested in a copied, isolated profile, with external test markers unchanged; that test did not start the Host or exercise real pending transactions. Do not delete the DSH home or shared inventory as part of package removal. To roll back, add a retained, verified compatible tarball. Cross-version rollback and journal compatibility remain unverified; do not downgrade across an incompatible journal format or use plugin replacement as a substitute for operation recovery.

## Verify a Registry

From `plugins/dsh`, with both development dependency sets installed:

```sh
bun run probe:registry --registry '<test Registry URL>' --namespace '<namespace>' --slug '<skill>' --version '<exact version>' --output /tmp/skillhub-registry-report.json
```

Use an anonymously downloadable published test skill and a new output filename. The probe sends anonymous GET requests, validates the archive in a temporary directory, and cleans that directory. It does not install into DSH or execute a skill.

The report separates required failures from optional-module results. Required search, resolution, metadata, documentation, or archive checks failing or being blocked produce exit code 1. Audit HTTP success is reported as `public-response-unvalidated`, not as a validated public audit schema. Run the probe separately for open-source and SaaS instances; one instance cannot establish compatibility for another.

## Validation and release boundaries

CI defines a Linux/macOS/Windows matrix on Node 24.2.0 and current Node 24 for shared CLI regression, plugin build/typecheck/lint/tests, and the candidate tarball. Each platform saves a separate artifact, and a failed job does not cancel the other platforms. This configuration is not evidence that all three platforms have passed; local evidence includes individual Linux and Windows checks, and remote CI has not yet run. macOS validation is deferred and does not establish platform support. The workflow runs for relevant pull requests, manual dispatch, and `dsh-v*` tag pushes. A tag build must match the plugin's package version. It does not publish npm packages, create Releases, or deploy services; creating a release tag remains a separate authorized action. Local evidence includes native pages, tool cards, upgrades/removals, interrupted-operation recovery, mobile layouts, and selected keyboard interactions. It does not replace the remaining platform and full accessibility acceptance.

The repository's authoritative scope, acceptance evidence, Registry/SaaS contract, and release process live in [OpenSpec](../../openspec/changes/add-dsh-native-plugin/README.md). That relative link is for repository browsing; the OpenSpec directory is not shipped in the npm tarball. For repeatable local Host probes, see [integration instructions](test/integration/README.md), also available in the source checkout only.

### Reproducible Host acceptance probes

`scripts/probe-host.mjs` runs the upgrade candidate, concurrent file change, plan expiry/session isolation, and source-change probes against a prepared temporary DSH Profile. It manages local Registry fixtures, copies the observer outside the package, stops its own child processes, and retains a unique result report. It does not install dependencies, invoke a model, or prove browser and cross-platform acceptance. Run `node scripts/probe-host.mjs --help`; environment preparation and individual commands are in the [integration guide](test/integration/README.md#可复跑的-host-计划与升级验收).


### Diagnostics for accepted operations

If installation, upgrade, or removal fails after acceptance, the Host writes `SkillHub accepted operation failed` to standard error with `operationId`, `kind`, and an allowlisted `code`. Correlate the operation ID with the durable record. Codes cover recognized filesystem/network errors and shared-core categories; `UNCLASSIFIED` does not identify a specific cause. Exception text, stacks, filesystem paths, Registry credentials, and request arguments are excluded.

This diagnostic neither replaces the operation journal nor retries work. Query the original operation for its outcome and recovery requirements. A crash or discarded process logs may prevent diagnostics from being retained. Previously lost exceptions cannot be recovered by this change.
