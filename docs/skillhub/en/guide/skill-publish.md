# Publish and Update Skills

## From Local Files to an Installable Version

A single-skill upload has one root `SKILL.md`. For multiple skills, use [Suite import](./suite-bundle.html); do not place unrelated skills in one single-skill ZIP.

1. Exercise the instructions, scripts and relative references in your local Agent, then prepare a standalone directory.
2. Choose a writable namespace and PUBLIC, NAMESPACE_ONLY or PRIVATE visibility. Document dependencies: publishing does not distribute your local environment.
3. Check the package preview, version, summary and warnings before submitting. Fix blocking errors; acknowledging a warning does not prove the package passed every check.
4. Follow the submitted version in My Skills through scanning, review or private publication confirmation.
5. Check access as an intended recipient, install in the target Agent and invoke it. Registry publication does not establish task effectiveness.

### Package Layout and Version Choice

```text
my-skill/
├── SKILL.md
├── scripts/       # Only when the skill needs scripts
└── references/    # Only when instructions reference these files
```

Relative references must resolve inside the package. Exclude credentials, caches and unrelated project files. `version` is optional: the server generates a time-based version when absent. Set it explicitly, for example `1.0.0` then `1.0.1`, for predictable releases. Top-level `version` takes precedence over `metadata.version`; do not supply conflicting values.

Resolve versions by an exact version, tag, or fingerprint; `^` / `~` ranges are unsupported. Tags such as `latest`, `beta`, and `stable` locate versions; `latest` follows publication state and the published pointer rather than the highest SemVer. Details expose package files and version/tag downloads.

### Updating an Existing Skill

Use Update in My Skills or publish again to the same namespace and skill name. Verify the coordinate: changing `name` may create a different skill. Published versions cannot be overwritten; use a new version for normal updates. While it awaits review, public users retain the previously published version. After approval, check the displayed version, title and summary.

Existing local installations do not update automatically. Consumers explicitly upgrade through the CLI. Suite members remain pinned until a new Suite version is published.

### Recovering from Submission Problems

| Symptom | Next step |
| --- | --- |
| Invalid frontmatter | Fix YAML and required fields, then preview again |
| Suspected secret or disallowed file | Remove sensitive content or fix the package; warning confirmation cannot bypass blocking errors |
| Version already exists | Inspect the existing version and use a new version for a normal update |
| Scan in progress | Wait for its status instead of repeatedly uploading the same version |
| `SCAN_FAILED` | Read the failure and use the retry action offered by the page; ask an operator to resolve infrastructure failures |
| Review rejected | Read the decision, revise the package and use the supported resubmission flow |
| Frozen namespace or removed membership | Restore legitimate write access first; a previously installed copy grants no publishing rights |


## Prepare metadata and choose a publishing entry

### Prepare metadata

Frontmatter requires `name` and `description`; `package.json` cannot replace skill metadata. Save this example as `my-skill/SKILL.md`:

```markdown
---
name: my-skill
description: Summarize a short text as numbered steps.
version: 1.0.0
---

Summarize the supplied text as numbered steps.
```

When packaging an existing skill, keep its required scripts, references,
`agents/` metadata, and license files together. Pass that skill directory
(`./my-skill` in this example) to the publish command below.

To show a human-readable or localized title in lists, search results, and detail pages,
add the optional `metadata.display-name` field:

```yaml
---
name: example-skill
description: An example skill for demonstration.
metadata:
  display-name: 示例技能
  version: "1.0.0"
---
```

`name` still determines the slug, installation coordinate, and links. The display name
must be a string of at most 200 characters after trimming surrounding whitespace.
Missing, null, or blank display names fall back to `name`. Titles follow the version's
review lifecycle: an update awaiting review does not replace an existing published title.
No database migration is needed; existing skills can keep their `name` and publish a new
version with this optional metadata.

On the web publish/update form, upload a package and edit **Display name**. The package's `metadata.display-name` (or `name`) pre-fills the field. Your edits take precedence and survive selecting another package; leave it blank to use the package title. The name is limited to 200 characters and follows this version's review. Avatar-only updates cannot rename a skill.

**My Skills** shows the pending update's title and summary with one main status and a plain-text comparison to the published version. Open the card to preview that update; public readers still see the published version. Details always show the copyable `@namespace/slug`; renaming does not change links or install identifiers.

`skillhub.example.com`, `YOUR_API_TOKEN`, and `my-team` are placeholders. Replace them with the instance, caller credential, and target namespace; never commit a real token.

Web names are stored in platform version metadata without rewriting `SKILL.md` or other package files. Downloads use the existing ZIP repackaging process, so compressed archive bytes may differ. CLI publishing keeps using package metadata. Approval, rollback after yanking, and promotion use the corresponding version's name and summary.

### Upload with the CLI

```bash
# Configure the SkillHub registry
export SKILLHUB_REGISTRY=https://skillhub.example.com
export SKILLHUB_TOKEN=YOUR_API_TOKEN

# Publish to default namespace
skillhub publish ./my-skill

# Publish to specific namespace
skillhub publish ./my-skill --namespace my-team
```

> The ClawHub CLI publish and sync protocols are not compatible with SkillHub. Use the SkillHub CLI above for publishing.

### Upload through the web

Open `/dashboard/publish` under the instance Web entry, select namespace, upload zip file, choose visibility, and click "Publish".

### Use the publishing API

```http
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

file: skill-package.zip
visibility: PUBLIC
```

![Ordinary public and private publication paths](/diagrams/skill-publish-lifecycle-sketch.png)

The diagram covers ordinary uploads; scan failures, review rejection and privileged publishing are described below.

## Submission is not publication

After uploading, inspect the returned status and the version in My Skills. For ordinary PUBLIC and NAMESPACE_ONLY submissions, scanning must be enabled: the version passes through `SCANNING` and then `PENDING_REVIEW`. Approval publishes it. A PRIVATE upload remains `UPLOADED` after any configured scan; use its publish confirmation action to publish it. Privileged automatic publishing follows a separate path.

`SCAN_FAILED` means the scan could not complete, not that the package was proven unsafe. A completed scan can still contain findings. A successful upload, HTTP response, or CLI exit is not evidence of `PUBLISHED`. During an update, public readers continue to see the published version while authorized maintainers can preview the pending version.

### Authoring drafts: create, edit, and validate structure

Use the ZIP or CLI upload path when a local skill directory already exists. Authoring drafts support file-by-file API editing; there is currently no web authoring workbench or endpoint that submits a draft as a published skill version. A draft does not automatically become an `UPLOADED` or review-pending version.

Creation requires a writable namespace and membership. `SUPER_ADMIN` bypasses membership only, not frozen/archived namespace checks. Reads, edits, and validation of existing drafts check ownership or `SUPER_ADMIN` without rechecking namespace state or membership; leaving or freezing a namespace does not automatically make an existing draft read-only. Use an authenticated browser session and the instance's CSRF token for writes. The current API Token route allowlist does not expose authoring endpoints; a CLI login token is not an authoring credential. There is no separate authoring enable switch in the source. Check an older instance's `/v3/api-docs` before using these routes.

These are HTTP contracts, not shell commands. Send requests through a same-origin API client in the authenticated instance, adding the deployment prefix for sub-path installations. `team`, `example-skill`, `{draftId}`, and `{runId}` are placeholders: replace them with an accessible namespace, a draft name, and IDs returned by the API.

| Operation | Request | Completion evidence | Explanation |
| --- | --- | --- | --- |
| Create draft | `POST /api/v1/authoring/drafts` | Response contains `data.id` and `data.revision` | Body requires `namespaceSlug` and `name`; `requirement` is optional; a `SKILL.md` scaffold is created |
| Read current content | `GET /api/v1/authoring/drafts/{draftId}`; `GET /api/v1/authoring/drafts/{draftId}/files` | Current revision and file inventory are readable | Read a file with `GET /api/v1/authoring/drafts/{draftId}/files/content?path=SKILL.md` |
| Save file | `PUT /api/v1/authoring/drafts/{draftId}/files` | Read response `data.draft.revision` | `expectedRevision` is the pre-write concurrency condition; changed content advances the revision, while a no-op may retain it; old validation does not cover new content |
| Start validation | `POST /api/v1/authoring/drafts/{draftId}/runs` | Response contains run `data.id` and `data.draftRevision` | The run captures that revision, not a publication request |
| Inspect result | `GET /api/v1/authoring/runs/{runId}`; `GET /api/v1/authoring/runs/{runId}/findings`; read `GET /api/v1/authoring/drafts/{draftId}` again | Run has `status=SUCCEEDED`, `errorCount=0`, and draft has `validated=true` | The run's `draftRevision` must equal the current draft `revision`; an old result does not validate new content |

Create request body:

```json
{"namespaceSlug":"team","name":"example-skill","requirement":"Turn meeting material into an action list"}
```

Text-file save body: replace `expectedRevision` with the revision just read, and `content` with the complete actual file. Do not keep reusing the example revision. `encoding` also accepts `base64` for binary files.

```json
{"path":"SKILL.md","content":"---\nname: example-skill\ndescription: Summarize meeting material\n---\n\n# Instructions\nExtract action items from the input.","encoding":"text","contentType":"text/markdown","expectedRevision":1}
```

Structure validation checks paths, duplicates, file counts and sizes, `SKILL.md` frontmatter, content/extension consistency, and compliance declaration structure. It does not execute skills, start a model or Agent, replace security scanning or human review, or establish compliance. For publication, assemble the edited files into a local directory and follow the normal upload path on this page. No draft ZIP export or direct-publication entry is currently delivered; reserved submission fields in a response do not establish that capability.

| Symptom | Likely cause | Locate the cause | Action |
| --- | --- | --- | --- |
| Endpoint absent | Instance version lacks it | Find the authoring paths in `/v3/api-docs` | Use local files and ZIP upload; do not guess a submit endpoint |
| Authentication or write rejected | Session, CSRF, or credential type mismatch | Check the post-login identity request and CSRF information | Log into the instance again; do not share cookies or tokens |
| Creation rejected | Missing membership or namespace not writable | Check membership and namespace state | Restore the prerequisites; do not use super-admin privileges to bypass frozen/archived checks |
| Existing draft rejected | Neither owner nor super administrator | Compare current account and draft ownership | Contact the owner; do not borrow credentials or assume leaving/freezing automatically removes existing draft write access |
| Revision conflict | Another edit advanced the revision | Read draft `revision` and file content again | Compare and merge changes; do not bypass revision checking |
| Errors or stale validation | Invalid structure or later edits | Inspect findings and compare run/draft revisions | Fix and validate again; do not treat an old successful run as publication |

Sources: [controller](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/authoring/SkillAuthoringController.java), [draft access/revisions](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/authoring/service/SkillDraftService.java), [structure validator](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/authoring/service/DraftStructureValidator.java), and [Token route policy](https://github.com/iflytek/skillhub/blob/main/server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/policy/RouteSecurityPolicyRegistry.java).

## Compliance Declarations

Skill authors can add `x-astron-compliance` to the `SKILL.md` frontmatter to declare how a skill version maps to compliance standards, controls, or security knowledge-base entries.

```yaml
---
name: incident-response-helper
description: Helps analysts draft incident response steps.
x-astron-compliance:
  - standard: mitre-attack
    version: "v19.1"
    controlId: T1059
    title: Command and Scripting Interpreter
    evidence:
      - type: packaged-file
        path: references/mitre-t1059.md
      - type: external-url
        url: https://attack.mitre.org/techniques/T1059/
---
```

Important boundaries:

- This is an author declaration, not a SkillHub endorsement or third-party certification.
- SkillHub validates the structure, duplicate mappings, packaged evidence paths, and external URL format.
- After publishing, the declaration is normalized into the version-level `complianceSnapshot` with a stable `digest`.
- Review pages show the diff when a later version adds, removes, or changes compliance declarations.
- Searching for `mitre-attack`, `T1059`, or the declaration title can discover the matching skill.

Field reference:

| Field | Required | Description |
|-------|----------|-------------|
| `standard` | Yes | Standard or framework identifier, such as `mitre-attack`, `nist-csf`, or `soc2` |
| `version` | Yes | Standard version |
| `controlId` | Yes | Control, technique, or clause ID |
| `title` | No | Human-readable control title; recommended for review and search |
| `evidence` | No | Evidence list, supporting packaged files and external URLs |

## Publishing and version query contracts

**Publish Skill Package**:
```http
POST /api/v1/skills/{namespace}/publish
Content-Type: multipart/form-data

# Parameters
file: MultipartFile (required)
visibility: PUBLIC | PRIVATE | NAMESPACE_ONLY (required)
```

**Parameter Reference**:
| Parameter | Type | Description |
|-----------|------|-------------|
| namespace | string | Namespace slug (path parameter) |
| file | MultipartFile | Skill package zip file |
| visibility | enum | Visibility level: PUBLIC, PRIVATE, NAMESPACE_ONLY |

**Get Skill Details**:
```http
GET /api/v1/skills/{namespace}/{slug}
```

**List Versions**:
```http
GET /api/v1/skills/{namespace}/{slug}/versions?page=0&size=20
```

**Get Version Details**:
```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}
```

**Download Specific Version**:
```http
GET /api/v1/skills/{namespace}/{slug}/versions/{version}/download
```

**Download by Tag**:
```http
GET /api/v1/skills/{namespace}/{slug}/tags/{tagName}/download
```

**Version Resolution**:
```http
GET /api/v1/skills/{namespace}/{slug}/resolve?version=1.2.0
```

## Package and identifier limits

- A skill package is limited to 100MB (configurable); size and file-count limits are separate.
- Skill slugs support lowercase letters, numbers, hyphens, and Unicode characters; inspect the final coordinate before publishing.
