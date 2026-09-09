# SkillHub CLI Operations

Use this reference after resolving the first-party CLI and authoritative registry in `SKILL.md`.
Run `skillhub help <command>` and `skillhub <command> --help` against that CLI before using a flag
not shown here. Use the globally installed, identity-checked `skillhub` command consistently; do not
switch to a per-operation package runner.

## Write Safety

Before a command writes local or registry state, establish the exact registry, coordinate and
optional version, Agent and scope or directory, existing installation ownership, and local-change
status. Treat every coordinate, version, query, and path supplied by a user as one quoted argument.

Start with the read-only operation in this table. Obtain explicit approval before the corresponding
write unless the user's current request already names that exact action and target.

| Task | Inspect first | Write |
|---|---|---|
| Install | `search`, `list` | `install` |
| Upgrade | `list`, `upgrade --check` | `upgrade` |
| Namespace sync | `sync status`, `sync diff`, `sync pull --check` | selected `sync pull` or `sync push` |
| Publish | inspect package, `publish --dry-run` | `publish` |
| Remove | `list` | precise `remove` |

Treat a current request that names the exact action and target as approval for that action. Otherwise,
obtain approval before `--force`, `--prune`, `remove --all`, remote removal, `--hard`, `logout`, or
`doctor`. Do not choose a commit, backup, deletion, or discard strategy when local changes block an
operation.

## Coordinates And Destinations

Accepted coordinates include `slug`, `namespace/slug`, `@namespace/slug`, and
`namespace--slug`. A bare slug resolves to `global` unless `--namespace` selects another namespace.
Use a full coordinate when known.

Use an Agent profile reported by live help and repeat `--agent` for multiple targets. For an
unsupported Agent, use an absolute `--dir` selected by the user. Do not combine `--dir` with
`--scope` or `--agent`.

After installation, run `list` with the same registry and Agent filter. Confirm the installed
version and that both `SKILL.md` and `.skillhub/metadata.json` exist.

## Upgrade An Installed Skill

Upgrade only explicitly named, SkillHub-managed installations. There is no implicit upgrade-all:

```bash
skillhub list --registry <registry> --json
skillhub upgrade '@team/code-review' --registry <registry> --check --json
skillhub upgrade '@team/code-review' --registry <registry>
```

Show the check plan before writing. Without approved `--force`, local changes block replacement.
Never bypass a downgrade, source conflict, unmanaged directory, fingerprint mismatch, or partial
target selection that cannot preserve one shared version.

## Synchronize A Namespace Workspace

Use `sync` only for an authenticated, non-`global` namespace. Inspect before pulling:

```bash
skillhub sync status --namespace team-a --dir <skills-dir> --registry <registry> --json
skillhub sync diff --namespace team-a --dir <skills-dir> --registry <registry>
skillhub sync pull --namespace team-a --dir <skills-dir> --registry <registry> --check
```

Outside an interactive terminal, select every write explicitly:

```bash
skillhub sync pull --namespace team-a \
  --skill code-review \
  --dir <skills-dir> \
  --registry <registry>
```

An empty interactive selection changes nothing. Do not add `--force` for local changes or `--prune`
for orphaned Skills without approval for the exact affected paths.

Before upload, validate without creating a version:

```bash
skillhub sync push --all \
  --namespace team-a \
  --dir <skills-dir> \
  --registry <registry> \
  --dry-run
```

Only add `--submit-review` after validation and confirmation. A submission may return `SCANNING`,
`UPLOADED`, `PENDING_REVIEW`, or `PUBLISHED`; only `PUBLISHED` proves immediate installability.

## Publish A Skill

Inspect the package and require a root-level `SKILL.md`. Validate against the selected registry:

```bash
skillhub publish ./my-skill \
  --namespace team-a \
  --visibility public \
  --registry <registry> \
  --dry-run
```

`--dry-run` sends the package bytes to the selected registry for validation. Obtain approval before
sending a local or private package that the user has not already asked to validate or publish. Fix
validation errors instead of forcing publication. Before repeating without `--dry-run`, confirm
the resolved namespace, slug, version, visibility, and included files. Report the returned lifecycle
status; a successful submission is not necessarily published.

## Remove Or Repair

List first, then use a full coordinate and the narrowest target filter:

```bash
skillhub list --agent codex --registry <registry>
skillhub remove '@team/code-review' --agent codex --registry <registry>
```

A bare-slug removal can match same-slug installations in multiple namespaces. Remote removal is
destructive: confirm the exact registry, namespace, and slug. `--hard` only suppresses an interactive
prompt; it never grants permission.

Use `skillhub doctor` to rebuild inventory after manual damage or stale records. Review its result
and retained backup. It does not resolve conflicting installed versions for the user.

## Troubleshoot

| Symptom | Check |
|---|---|
| Unknown command or option | Check CLI identity and both live help surfaces; update only with approval. |
| Authentication failure | Confirm registry, run `whoami`, and have the user refresh credentials privately. |
| Wrong installation directory | Inspect `list --json`; reinstall only after choosing explicit scope, Agent, or directory. |
| Install or upgrade blocked | Preserve files; inspect source ownership, metadata, version direction, and local changes. |
| Publish validation failed | Fix the reported package, metadata, permission, or scanner issue. |
| Inventory stale | Run `doctor`, review its result, and keep its backup. |
| Registry error | Preserve the public message and `requestId`; do not guess the server-side cause. |

Report the registry, coordinate and version, Agent, scope or directory, preview performed, files
changed, and verification result. For publish and sync push, report the actual lifecycle status.
