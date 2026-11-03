# Frontend analytics: deployment and setup

SkillHub supports configuration-only integration with Umami and DataFinder. Collection is off by default. Enabled collection observes configured actions without making normal business operations wait for the statistics platform. No business JS edits or hand-written Nginx injection are required.

Use a **Web image containing this feature**. Pin deployment-file source and image tags separately and match their versions: updating `runtime.sh` alone cannot add an adapter to an older image. See the [detailed operator guide](./frontend-analytics-guide.md) (Chinese) and the [interface and privacy contract](./frontend-analytics-reference.md) (English).

## Choose a deployment path

| Situation | Approach | Instructions |
| --- | --- | --- |
| Script-installed SkillHub, no statistics platform | Start independent Umami and PostgreSQL, obtain a website UUID, then enable collection | [Operator guide, section 3](./frontend-analytics-guide.md) |
| Existing Umami or DataFinder | Supply the platform's public identifier and collection origin, then recreate Web; no local statistics services | [Operator guide, sections 2 and 3.1](./frontend-analytics-guide.md) |
| Kubernetes / Helm | Deploy the statistics platform independently and connect SkillHub using Service DNS | [Operator guide, section 4](./frontend-analytics-guide.md) |

## Deploy the platform before obtaining its UUID

1. Follow the operator guide to fetch the selected version of `runtime.sh`, choose a persistent runtime directory, and retain existing business configuration.
2. Run `analytics-up` to fetch the supporting files and start independent Umami and PostgreSQL.
3. Open the printed administration URL. Its default binding is `127.0.0.1:8300`; use SSH forwarding for a remote host.
4. Sign in, immediately change the initial password, create a website, and copy its **UUID**.
5. Run `analytics-enable --website-id <website UUID>` to write the configuration and recreate Web.
6. Refresh the page, trigger configured actions, and verify records in Umami.

Starting Umami does not require a website UUID. SkillHub can also run normally with collection disabled while the platform is being prepared.

Existing-platform users skip platform installation. Umami needs its website UUID; DataFinder needs its numeric app ID and collection origin. Never place administration passwords, API tokens or App Keys in browser configuration.

## Independent lifecycle

The browser sends events to a same-origin SkillHub Web endpoint, which forwards them to the platform. For local Umami, only Web and Umami join the shared private network; the SkillHub backend and business database remain outside it.

- `.env.release` holds business and Web collection configuration.
- `analytics/.env` holds independent credentials, project and network identity. Once initialized, those identifiers are authoritative; renaming them is not a data migration.
- `analytics-disable` disables collection and recreates Web while the platform stays running.
- `analytics-down` stops statistics services without deleting credentials or data.
- `down` and `clean` affect SkillHub. When the independent analytics directory exists, `clean` retains it and its data. Restore business configuration before redeploying SkillHub.

The operator guide covers legacy identity migration, multiple instances, upgrades and recovery.

## Verify behavior

Disabled or unauthorized new pages must load no collection module or SDK and send no events. Enabled pages should send only configured actions, without search text, real resource paths or login credentials. Platform failures must leave normal business behavior intact.

HTTP 200 alone is not evidence of storage. Verify records in the platform; DataFinder additionally requires `e:0` and `sc:1`. Actions describe intent rather than successful download, installation or publication.

`consent=granted` records the deployment's authorization; it does not mean every user clicked a consent dialog. Integrate an existing consent component when per-user approval is required.
