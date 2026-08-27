# SkillHub Python Examples

A minimal, dependency-light (`requests`-only) Python client and runnable
examples for the SkillHub REST API. Use it to **search, inspect, download,
and publish** skills from Python — the same operations the ClawHub CLI
performs, without shelling out to the CLI.

> These are reference examples, not (yet) an officially published pip
> package. See [iflytek/skillhub#701](https://github.com/iflytek/skillhub/issues/701)
> for the discussion on whether to ship a full published SDK.

## Files

| File | What it is |
|------|------------|
| [`skillhub_client.py`](./skillhub_client.py) | A small `SkillHubClient` class wrapping the REST API |
| [`example_usage.py`](./example_usage.py) | Runnable script: search → resolve → download, and publish |
| [`requirements.txt`](./requirements.txt) | The only dependency: `requests` |

## Setup

```bash
pip install -r requirements.txt

# Point at your SkillHub instance
export SKILLHUB_URL=https://skill.example.com
# Only needed for write operations (publish / star / rate)
export SKILLHUB_TOKEN=<your-api-token>
```

Generate an API token from the SkillHub web UI (**Settings → API Tokens**) or
via `POST /api/v1/tokens`.

## Quick start

```python
from skillhub_client import SkillHubClient

client = SkillHubClient()  # reads SKILLHUB_URL / SKILLHUB_TOKEN from env

# Search public skills
results = client.search(keyword="email", size=5)

# Inspect and resolve a version
detail = client.get_skill("my-namespace", "my-skill")
resolved = client.resolve("my-namespace", "my-skill", tag="stable")

# Download the latest package (returns the written file path)
path = client.download("my-namespace", "my-skill")

# Publish a skill package (requires a token)
client.publish("./my-skill.zip", namespace="my-namespace")
```

Or run the end-to-end script:

```bash
python example_usage.py                          # search + inspect + download
python example_usage.py publish ./my-skill.zip my-namespace
```

## Supported operations

| Method | Endpoint | Auth |
|--------|----------|------|
| `search(keyword, namespace, page, size)` | `GET /api/v1/skills` | — |
| `get_skill(namespace, slug)` | `GET /api/v1/skills/{ns}/{slug}` | — |
| `list_versions(namespace, slug)` | `GET /api/v1/skills/{ns}/{slug}/versions` | — |
| `resolve(namespace, slug, version, tag)` | `GET /api/v1/skills/{ns}/{slug}/resolve` | — |
| `download(namespace, slug, version, dest)` | `GET /api/v1/skills/{ns}/{slug}[/versions/{v}]/download` | — |
| `whoami()` | `GET /api/v1/whoami` | Bearer |
| `publish(zip_path, namespace, request_id)` | `POST /api/v1/publish` | Bearer |
| `star(namespace, slug)` | `POST /api/v1/skills/{ns}/{slug}/star` | Bearer |
| `rate(namespace, slug, score)` | `POST /api/v1/skills/{ns}/{slug}/rating` | Bearer |

The client unwraps the unified `{code, msg, data}` response envelope
automatically and raises `SkillHubError` on a non-zero business code.

## Notes

- Write operations accept an optional `request_id` (a UUID) that is sent as
  the `X-Request-Id` header for idempotency.
- For the full API surface (namespaces, reviews, promotion, tags), see the
  [Developer Docs → API](https://iflytek.github.io/skillhub/) and
  [`document/docs/04-developer/api`](../../document/docs/04-developer/api).
