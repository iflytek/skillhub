# CLI Namespace Errors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every documented namespace coordinate reach the correct registry path and preserve public server error messages and request IDs without misclassifying all 403 responses as token-scope failures.

**Architecture:** Extend the shared coordinate parser with a resolver that owns explicit namespace conflict handling, then make install/remove consume it while removing the argument parser's early `global` default. Add one response-error converter inside `SkillHubClient` so JSON endpoints and downloads share safe `msg`/`requestId` extraction while retaining status-based exit codes.

**Tech Stack:** TypeScript, Bun test/build, cac, npm package tarballs.

## Completion record

Completed in PR #608 and revalidated after merging `origin/main` on 2026-07-29.
The checklist below reflects the delivered implementation. The maintainer
revalidation did not recreate historical RED states; it reran the current
GREEN gates with the repository-pinned Bun 1.3.13:

- Focused namespace/error/help regression: 142 tests passed.
- Complete CLI regression: 379 tests passed with
  `bun test --max-concurrency=1` (peak RSS 180452 KiB).
- Typecheck, lint, and build passed.
- The packed `@astron-team/skillhub@0.1.9` artifact contained `dist/index.js`,
  `README.md`, `CHANGELOG.md`, `LICENSE`, and `package.json`.
- Packed Node artifact smoke passed for `version`, `help install`, all three
  namespaced coordinate forms, coordinate/`--namespace` conflict handling, and
  structured 403 message/request-ID rendering.
- The Chinese and English VitePress documentation build passed.

---

### Task 1: Establish release artifact baseline

**Files:**
- Inspect: `cli/package.json`
- Inspect: npm package `@astron-team/skillhub@0.1.9`

- [x] **Step 1: Read published metadata and download the package**

Run:

```bash
npm view @astron-team/skillhub@0.1.9 version dist.tarball dist.integrity --json
npm pack @astron-team/skillhub@0.1.9 --pack-destination /tmp/skillhub-npm-019-issue-606 --json
```

Expected: version `0.1.9`, a tarball with `dist/index.js`, `README.md`,
`LICENSE`, and `package.json`.

- [x] **Step 2: Confirm the published bundle contains both bug signatures**

Run:

```bash
tar -xOf /tmp/skillhub-npm-019-issue-606/astron-team-skillhub-0.1.9.tgz package/dist/index.js | rg 'indexOf\("--"\)|token may lack required scope'
```

Expected: both patterns are present, proving 0.1.9 includes the double-dash
parser but also the misleading 403 fallback.

### Task 2: Normalize coordinates and reject conflicts

**Files:**
- Modify: `cli/test/unit/shared/skill-name-parser.test.ts`
- Modify: `cli/src/shared/skill-name-parser.ts`

- [x] **Step 1: Replace permissive edge tests with the public coordinate matrix**

Add table-driven assertions for `my-skill`, `team/my-skill`,
`@team/my-skill`, and `team--my-skill`. Add resolver assertions for an explicit
namespace on a bare slug, a matching coordinate namespace, and a conflicting
namespace. Add malformed-input assertions for empty or incomplete coordinates.

- [x] **Step 2: Run the parser test and verify RED**

Run:

```bash
cd cli && bun test test/unit/shared/skill-name-parser.test.ts
```

Expected: failures for slash forms, malformed input, and the missing resolver.

- [x] **Step 3: Implement the minimal parser and resolver**

Keep `ParsedSkillName` unchanged. Add `resolveSkillName(skillName,
explicitNamespace?)` returning `ParsedSkillName`. It calls one internal parser,
applies `global` only to bare slugs, accepts a matching explicit namespace, and
throws `CliError(..., EXIT.usage)` on malformed input or conflict.

- [x] **Step 4: Run the parser test and verify GREEN**

Run:

```bash
cd cli && bun test test/unit/shared/skill-name-parser.test.ts
```

Expected: all parser tests pass with no warnings.

### Task 3: Wire the resolver through real CLI parsing

**Files:**
- Modify: `cli/src/commands/install.ts`
- Modify: `cli/src/commands/remove.ts`
- Modify: `cli/src/index.ts`
- Modify: `cli/test/unit/commands/install-command.test.ts`
- Modify: `cli/test/integration/install-command.test.ts`

- [x] **Step 1: Add failing command and integration tests**

Capture `installSkill` options in the unit test and assert a namespaced
coordinate passes `namespace: 'team'` and `slug: 'my-skill'`. In the integration
test, register a `team/my-skill` fixture and execute:

```text
skillhub install @team/my-skill --dir <temp> --registry <fake> --token sk_ok --json
```

Assert exit 0, JSON namespace `team`, and fake-registry resolve state
`{ namespace: 'team', slug: 'my-skill' }`. Add a conflicting
`--namespace other` case that exits with usage code 5 before registry access.

- [x] **Step 2: Run the focused command tests and verify RED**

Run:

```bash
cd cli && bun test test/unit/commands/install-command.test.ts test/integration/install-command.test.ts
```

Expected: the namespaced integration case resolves `global` or fails, and the
conflict case does not produce the expected usage error.

- [x] **Step 3: Use `resolveSkillName` and remove the cac default**

Change install/remove to call:

```typescript
const { namespace, slug } = resolveSkillName(skillNameArg, options.namespace)
```

Change install's option declaration to:

```typescript
.option('--namespace <slug>', 'Namespace for a bare skill slug')
```

- [x] **Step 4: Run the focused command tests and verify GREEN**

Run the same Bun test command. Expected: all focused command tests pass.

### Task 4: Preserve structured API errors and request IDs

**Files:**
- Modify: `cli/test/unit/clients/skillhub-client.test.ts`
- Modify: `cli/test/unit/shared/output.test.ts`
- Modify: `cli/src/clients/skillhub-client.ts`
- Modify: `cli/src/shared/output.ts`

- [x] **Step 1: Add failing response and output tests**

Add client tests for:

```typescript
Response.json(
  { code: 403, msg: 'token has been revoked', requestId: 'req-403' },
  { status: 403 }
)
```

Assert message `token has been revoked`, auth exit code, and details containing
`requestId: 'req-403'`. Add 403 tests without `msg`, with invalid JSON, and a
404 with structured fields. Add a download 403 structured-response test. Add a
human output assertion for `Request ID: req-403`.

- [x] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd cli && bun test test/unit/clients/skillhub-client.test.ts test/unit/shared/output.test.ts
```

Expected: structured messages/request IDs are discarded and human output omits
the request ID.

- [x] **Step 3: Implement one safe response-error converter**

Inside `SkillHubClient`, add a private method that reads non-success bodies once,
parses only object-shaped JSON, accepts only non-empty string `msg` and
`requestId`, selects status-specific fallback text and exit codes, and returns a
`CliError`. Use it from both `handleJsonResponse` and `download`. Do not add the
old token-scope hint to 403 errors. Update `renderError` with:

```typescript
if (typeof cliError.details.requestId === 'string') {
  lines.push(`Request ID: ${cliError.details.requestId}`)
}
```

- [x] **Step 4: Run focused tests and verify GREEN**

Run the same focused Bun test command. Expected: all client/output tests pass.

### Task 5: Document the public contract and release impact

**Files:**
- Modify: `cli/src/commands/help.ts`
- Modify: `cli/README.md`
- Create: `cli/CHANGELOG.md`
- Modify: `cli/package.json`
- Modify: `cli/test/integration/help-command.test.ts`

- [x] **Step 1: Add a failing help assertion**

Assert `skillhub help install` includes `@team/my-skill`,
`team/my-skill`, and `team--my-skill` examples.

- [x] **Step 2: Run the help test and verify RED**

Run:

```bash
cd cli && bun test test/integration/help-command.test.ts
```

Expected: the coordinate examples are absent.

- [x] **Step 3: Update help, README, and release notes**

Use `<coordinate>` in install usage. Document all accepted forms and the
same-namespace/conflict rule. Add an Unreleased changelog entry covering
coordinate normalization and structured 403 messages/request IDs. Include
`CHANGELOG.md` in the npm package `files` list.

- [x] **Step 4: Run the help test and verify GREEN**

Run the same Bun test command. Expected: all help tests pass.

### Task 6: Verify source, build, and packed artifact

**Files:**
- Verify: all files changed by Tasks 2-5
- Produce locally: `cli/dist/index.js`
- Produce locally: npm tarball under `/tmp`

- [x] **Step 1: Run the complete CLI quality gate**

Run:

```bash
cd cli && bun test
cd cli && bun run typecheck
cd cli && bun run lint
cd cli && bun run build
```

Expected: every command exits 0 with no errors or warnings.

- [x] **Step 2: Pack and inspect the candidate artifact**

Run:

```bash
cd cli && npm pack --pack-destination /tmp/skillhub-cli-issue-606 --json
tar -tf /tmp/skillhub-cli-issue-606/astron-team-skillhub-0.1.9.tgz
```

Expected: the package contains the built executable, README, changelog,
license, and package metadata.

- [x] **Step 3: Run packed-bundle smoke checks**

Extract the tarball to a temporary directory and run the built executable's
`version` and `help install` commands. Expected: version reports 0.1.9 and help
shows every coordinate form. Run the relevant unit/integration suites against
source to verify request paths and structured errors.

- [x] **Step 4: Review the diff and commit**

Run:

```bash
git diff --check
git status --short
git diff --stat
```

Expected: only CLI implementation/tests/docs and the two planning documents are
changed; generated `cli/dist/index.js` and tarballs are not committed.

Commit with a conventional message containing the issue ID:

```bash
git commit -m "fix(cli): normalize namespace coordinates and errors (#606)"
```

### Task 7: Review and create the single final PR

**Files:**
- Review: committed diff against `origin/main`

- [x] **Step 1: Run tester and reviewer gates**

The tester must confirm focused and full CLI gates plus package smoke evidence.
The reviewer must inspect coordinate compatibility, error disclosure, test
coverage, docs, commit metadata, and absence of unrelated changes. Resolve all
blocking findings before continuing.

- [x] **Step 2: Push only the assigned branch**

Run:

```bash
git push -u origin fix/cli-namespace-errors
```

Expected: only the assigned branch is created or updated remotely.

- [x] **Step 3: Create one PR linked to the issue**

Create one PR titled `fix(cli): normalize namespace coordinates and errors`
with `Related to #606` in the body, complete test/package evidence, docs and
risk sections, and no close intent unless the project manager requests it.
Do not merge the PR.
