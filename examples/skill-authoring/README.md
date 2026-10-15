# Example skill: text-stats

A complete, self-contained example for the Skill Authoring & Validation
platform. It demonstrates every artifact an author must produce and every
script assertion type the behavior layer can check.

```
examples/skill-authoring/
├── SKILL.md              # skill description: frontmatter (name, description) + usage docs
├── validation.yaml       # behavior validation cases (4 tasks, 6 assertion types)
├── scripts/
│   └── text-stats.sh     # the executable helper: text file → JSON statistics
└── references/
    ├── metrics.md        # background material the skill cites
    └── sample.txt        # fixture input with known counters (2 lines, 20 words)
```

The skill computes word/line/character statistics for a text file and emits a
single-line JSON object, which it also mirrors to `artifacts/report.json`:

```json
{"input":"references/sample.txt","lines":2,"words":20,"characters":144}
```

## Validation cases

| Task                 | What it proves                                        | Assertion types used |
| -------------------- | ----------------------------------------------------- | -------------------- |
| `fixture-stats`      | counters are correct for the bundled fixture          | `exit_code`, `stdout_contains`, `stdout_json`, `tool_call_count` |
| `output-shape`       | stdout is one JSON object with exactly the known keys | `exit_code`, `stdout_matches` |
| `report-artifact`    | the run leaves a persistent report artifact          | `exit_code`, `artifact_exists` |
| `rejects-missing-file` | bad usage fails loudly instead of printing garbage  | `exit_code` |

A valid run finishes `SUCCEEDED` with 4 tasks and 0 findings. To see the fix
loop in action, change the `/words` expectation to `21` and re-validate: the
run fails with an `ASSERTION_FAILED` finding pointing at the exact task.

## Loading it into the platform

### Option A: web workbench

1. Sign in and open **Skill 创作** (Authoring) from the sidebar.
2. Create a draft named `text-stats` in any namespace you own.
3. In the **Files** tab, replace the scaffolded `SKILL.md`, then create
   `validation.yaml`, `scripts/text-stats.sh`, and the two `references/`
   files with the contents from this directory (paths matter, names are
   up to you).
4. In the **Runtime** tab bind agent type **local-script** with interpreter
   `sh`, no tool allowlist, no MCP servers.
5. Click **Validate** and watch the run stream in real time — structure,
   config, then behavior events appear as they happen.
6. When the run succeeds, **Submit** pushes the draft into the existing
   scan/review/publish pipeline.

### Option B: REST API

With the dev profile running (mock auth), the same flow is four calls —
see `scripts/authoring-smoke-test.sh` for a complete, runnable version
including CSRF bootstrapping:

```sh
BASE=http://localhost:8082
H='-H Content-Type:application/json -H X-Mock-User-Id:local-user'

# 1. create the draft (id from the response)
curl -s $H -X POST $BASE/api/web/authoring/drafts \
  -d '{"namespaceSlug":"global","name":"text-stats","requirement":"text statistics as JSON"}'

# 2. save each file (repeat for every path above)
curl -s $H -X PUT $BASE/api/web/authoring/drafts/<draftId>/files \
  -d '{"path":"SKILL.md","content":"<contents of SKILL.md>"}'

# 3. bind the runtime
curl -s $H -X PUT $BASE/api/web/authoring/drafts/<draftId>/runtime \
  -d '{"agentType":"local-script","config":{"interpreter":"sh"},"toolAllowlist":[],"mcpServers":[]}'

# 4. run validation, then poll the run / stream its events
curl -s $H -X POST $BASE/api/web/authoring/drafts/<draftId>/runs
curl -sN $BASE/api/web/authoring/runs/<runId>/events/stream   # live SSE
```

Scripts execute in an isolated workspace with a scrubbed environment (only
`PATH`, `HOME`, `LANG` plus variables named in the binding's `envAllowlist`
pass through), so the example deliberately sticks to `wc`/`tr`/`printf`,
which every Unix provides. Allowed interpreters are `sh`, `bash`, `python3`,
and `node`.
