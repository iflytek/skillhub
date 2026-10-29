# Find and install skills

Find a skill in an existing registry, inspect an available version, then install it for your Agent. Search results, Web details, and local installation are separate stages: seeing a card does not mean the skill is installed.

## Before you start

Public skills can be browsed anonymously. For team or private resources, sign in to the correct instance and confirm namespace permissions. See [Quick Start](../quickstart.md) for CLI and Agent setup. Paths below are relative to your deployment, not a fixed development port.

## Search in the Web UI

1. Use the homepage search box or open `/search`; enter a task keyword such as `pdf`.
2. Restrict to a namespace with `@team pdf`, or browse it with `@team`. Replace `team` with a real namespace slug.
3. Select a label to narrow results; select it again to clear it. The current Web selector allows one label at a time.
4. Sort by relevance, downloads, or newest. The Web default is newest; rating is not a separate sort option.
5. Open a card and inspect the coordinate, instructions, versions and files before installing.

Query, namespace, label, sort and page are retained in the URL, so links can be shared and results revisited. Queries are limited to their first 50 characters; reduce a long task description to keywords.

## Search only your stars

Enable the starred-only filter after signing in; anonymous users are directed to login. This mode matches names, summaries, namespaces and slugs within fetched starred skills rather than running the ordinary full-text search. It supports namespace filtering and download sorting; relevance and newest both sort by update time. The current starred branch does not apply label filtering: turn it off before searching by label.

`/dashboard/stars` provides paginated browsing. See [Stars, ratings and notifications](./social.md).

## Decide what to install

Read the instructions and check required scripts, tools and services. Install with `@namespace/slug`; a display-name change does not change this coordinate.

A related-Suite badge means the skill belongs to a Suite visible to you. Details distinguish entry and ordinary members and show companion skills. One skill can belong to multiple Suites. Use the [Suite guide](./skill-suites.md) for the collection, or install the skill independently.

## Install and verify with the CLI

Replace the example coordinate with a real search result. Keep the default registry and your private instance distinct.

```bash
skillhub search pdf
# Run from the target project root
skillhub install @team/example-skill --scope project --agent codex
skillhub list --agent codex
# Pin a specific release when needed
skillhub install @team/example-skill --version 1.0.0 --scope project --agent codex
```

Ordinary CLI search returns Skills, not Suites. It does not expose all Web filters; check `skillhub help search`. Add `--registry https://skillhub.example.com` for a self-hosted instance.

Check the reported destination and its `SKILL.md`, let your Agent discover the skill, then provide a task. Downloading a ZIP only obtains files; it does not place them in an Agent directory. See [CLI reference](./cli.md) for upgrades, conflicts and removal.

## Troubleshooting

| Symptom | Check and action |
| --- | --- |
| No results | Clear label, namespace and starred-only filters; try a shorter keyword and verify the registry |
| A colleague can see it | Verify identity and namespace permissions; PRIVATE is not shared with every member |
| A new submission is missing | Check its scan/review state in My Skills; accepted is not published, and indexing may lag |
| Visible card, failed download | Reopen details and check the version, permissions and current resource state; retain the error and requestId |
| Agent does not discover the installation | Check project root, scope, agent and SKILL.md against CLI output; reload using the Agent's mechanism |

Search rank is not a safety or quality certification. Read instructions and scan results before choosing.

## Search API boundaries

```text
GET /api/web/skills?q=pdf&namespace=team&label=data-processing&sort=downloads&page=0&size=20
```

`q`, `namespace` and `label` are optional. The API accepts repeated labels, while the Web selector is single-select. Sort values are `relevance`, `downloads` and `newest`; pages start at zero. API pages default to 20 items; the Web page uses 12. Results include `items`, `total`, `page` and `size`, filtered for the current identity. Consult the instance OpenAPI for the response envelope.

## Visual walkthrough

![Concept Diagram](/diagrams/skill-discovery-concept.png)

![Screenshot](/screenshots/skill-discovery-search.png)

![Flow Diagram](/diagrams/skill-discovery-flow.png)
