# Frontend analytics: first local setup

This walkthrough adds a local demo entry in front of an existing SkillHub Web and verifies events stored in Umami. Collection is off by default.

::: warning Current support scope
The one-click script can install independent Umami from an empty directory or connect an existing platform; Helm can deploy analytics as a separate unit. Follow the [detailed guide](./frontend-analytics-guide.md) (Chinese). The remainder is the older manual local demo pinned to `v0.2.25`, kept for background.
:::

## 1. Prepare Web and the example files

Confirm your existing SkillHub Web opens and search works. Not deployed yet? Complete [Docker deployment](./deployment.md) first. This example assumes a local root-path deployment; use the [detailed guide](./frontend-analytics-guide.md) for sub-paths, production proxies, and CSP. Docker, Compose, Git, and unused local ports 8300 and 8081 are required.

| Address / port | Purpose |
| --- | --- |
| Original SkillHub Web (default 80) | Existing application; this demo does not redeploy it |
| `http://localhost:8300` | Umami administration: create a website and inspect events |
| `http://localhost:8081` | New demo SkillHub entry: edge injects the collector script |

One-click installation directories do not contain the example sources. Get the demo files from the fixed `v0.2.25` tag in a separate working directory:

```sh
git clone --depth 1 --branch v0.2.25 https://github.com/iflytek/skillhub.git skillhub-analytics-demo
cd skillhub-analytics-demo
```

Use an unused destination if that directory exists. This fetches demo assets, not a replacement SkillHub deployment; do not reinstall your existing application. If using an existing checkout, confirm it contains `docs/examples/analytics/compose.yaml`. Run every following shell command from the **repository root in the same terminal**, not the runtime installation directory.

The example connects to the original Web on host port 80 by default. If its actual port is 18080, first set:

```sh
export SKILLHUB_WEB_UPSTREAM=host.docker.internal:18080
```

Replace `18080` with the actual **Web** port, not the backend port. Do not include `http://` or a path. Web must listen on an address reachable by containers; loopback-only binding or firewall rules can block edge. The example includes the `host-gateway` mapping for native Linux.

## 2. Start Umami and obtain a website UUID

Before starting, run `docker compose ls --all` and confirm the dedicated project name `skillhub-analytics-demo` is not used by another project. If occupied, choose an unused name and replace it consistently in every command. Explicit `-p` prevents inheriting an existing terminal `COMPOSE_PROJECT_NAME` and targeting another deployment; cloning into a different directory does not itself isolate the Compose project.

Check the example configuration, then start only its database and Umami:

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml config
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml up -d postgres umami
```

Wait for `http://localhost:8300`, sign in with demo credentials **`admin` / `umami`**, and change the password immediately. Create a demo website in website management and copy its **UUID**, not its name, URL, or your password.

This Compose uses public demo secrets and a temporary database. It does not provide a production persistence, backup, or access-control solution; do not deploy it unchanged in production.

## 3. Configure the demo actions

Open `docs/examples/analytics/observe/v1/observer.mjs` in the repository. Preserve the imports, `start()`, `stop()`, and other code; replace only `const config` and `hasConsent()` with:

```js
const config = Object.freeze({
  enabled: true,
  website: 'REPLACE_WITH_YOUR_WEBSITE_UUID',
  prefix: '/_skillhub-umami',
  rules: DEFAULT_RULES,
})

// Only for an isolated demo where you explicitly consent to collection.
// Production integrations must read the actual consent state.
function hasConsent() { return true }
```

`DEFAULT_RULES` is already imported and covers existing actions such as search submission and skill cards. The original `skill.example.open` has no corresponding product element, so it is omitted here. Never put administration secrets or personal data in this file, and do not use a fixed `true` for other users.

## 4. Start the demo entry and verify stored events

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml up -d --force-recreate edge
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml exec edge nginx -t
```

Expect a successful Nginx check. Open **`http://localhost:8081`**, rather than the original Web URL, to test collection.

1. In developer tools Network, enable **Disable cache** and **Preserve log**, refresh, and filter `/_skillhub-umami/`. Expect `script.js` to load successfully, without an automatic pageview.
2. Enter test text and click Search: expect one `api/send` POST. Search again with Enter: expect one additional event.
3. Inspect the payload: `name` is `search.query.submit`, and `data.event` is `submit`. Search terms and identity fields must be absent. Open a skill card to check `skill.card.open`.
4. In Umami on port 8300, open the website events view and select the current time range. Confirm matching events and counts. **HTTP 200 alone does not prove storage.** Click events express intent, not successful downloads, publishing, or execution.

| Additional acceptance check | Expected result |
| --- | --- |
| Disable collection and refresh | No SDK / event requests; application still works |
| Stop demo Umami while collection is enabled | Collection fails; search and skill pages still work |
| Start Umami and refresh | New events are stored; lost events are not guaranteed to replay |

The [detailed guide](./frontend-analytics-guide.md) provides fault-test controls and recovery steps. Operate only this demo, never stop a shared platform.

## 5. Disable and clean up

Run `window.skillhubAnalytics.stop()` in the current page to stop future dispatch; already dispatched requests cannot be recalled. For persistent disabling, restore `enabled: false` and make `hasConsent()` return `false`, then recreate edge and refresh with cache disabled.


```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml up -d --no-deps --force-recreate edge
```

To pause the demo while keeping its containers for continued use:

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml stop
```

The demo database is not a production persistence solution. Back up required data first; stopping is not a durability guarantee. Only when demo data is no longer needed, run:

```sh
docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml down -v
```

This removes the example containers and volumes; treat demo data as deleted. Do not run cleanup against the existing SkillHub deployment. Return to the original Web URL afterward.

## Common problems

| Symptom | Check first |
| --- | --- |
| Compose file not found | Work from the cloned repository root, not the runtime installation directory |
| Ports 8300 / 8081 cannot start | Local port conflicts; inspect `docker compose -p skillhub-analytics-demo -f docs/examples/analytics/compose.yaml logs` |
| 8081 returns 502 | Web port, container-reachable binding, and firewall; do not use the backend port |
| No collection requests | Visit 8081, verify enabled / consent, recreate after editing, and refresh with cache disabled |
| Requests fail or no stored events | Correct UUID, Umami readiness, matching time range, and the actual Network response |

Further reading: [complete setup guide (Chinese)](./frontend-analytics-guide.md) · [interface and privacy contract](./frontend-analytics-reference.md). Minimal fields do not mean anonymous transport: IP and User-Agent may still reach the platform. Deployment owners handle retention, access control, and compliance.
