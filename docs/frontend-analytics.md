# Passive frontend analytics attributes

For step-by-step setup, see the [Chinese integration guide](frontend-analytics-integration.md).
This document is the English contract reference. Configuration-only deployment is
planned in [PR-2](../openspec/changes/add-configurable-frontend-analytics/proposal.md);
the current examples remain deployment-owned and opt-in.

SkillHub exposes semantic HTML attributes for separately maintained, optional
collectors. It does not collect or send analytics by default. There is no SDK,
vendor dependency, vendor token, network reporter, app script loader or
telemetry configuration switch. The application entrypoint and production Nginx
configuration are unchanged. Deployment owners control consent, script delivery,
retention, transport and compliance; this is not an official vendor integration
or a durable business-event feed.

## Contract and scope

`data-analytics-id` names an action category (what kind of action), not a unique
event or instance ID, and describes intent, not its result. Many elements and
many events share the same category. Lowercase dotted names are independent of
language, visible text and layout.

The example callback payload is `{ id, event }`: `id` is the matched category;
`event` is the native DOM event type observed (`click`, `submit`, or `keydown`),
not a business result. Evolution within this module version is additive only:
new optional fields must be allowlisted static values, consumers ignore unknown
keys, and existing field meanings never change. Breaking changes use a new
versioned module path.

Published IDs are frozen. A meaning change needs a new ID; rename/removal must be
called out in the PR and release notes and visible in the generated manifest diff
(the CI drift test checks source against that manifest). Deployers should diff
the manifest between versions: configured IDs that disappear simply never fire;
new IDs remain inert until explicitly configured. The manifest is not a runtime
closed enum.
Literal conditional branches distinguish finite business actions (for example,
namespace freeze/unfreeze/archive/restore/delete and each confirmation/cancel).
No resource, slug, version, user, provider, role, label instance or backend string
is interpolated into attributes. Fixed navigation metadata is source-owned.
`data-analytics-event`, where present, describes a native observation boundary;
it is a hint for a deployment collector, not an application event dispatcher.

The source inventory covers these domains:

- Landing/catalog/search: queries, sorting, filters, results, pagination and links.
- Skills: detail structure, tabs/versions/comparison, documentation and files,
  acquisition/copy, social actions, labels, visibility and lifecycle confirmations.
- Publishing: package/folder selection, preview/retry, submission/warnings and
  avatar editing, staging, retries and cancellation.
- Suites: catalog/detail, members/versions, editor/import, publishing tasks,
  lifecycle and destructive actions.
- Reviews, promotions, reports and governance: queues, detail inspection,
  dispositions, layered confirmations, rejection editing, cancellation and retry.
- Namespaces: management, membership/import, invitations, ownership and lifecycle.
- GitHub: collection/settings, discovery, credentials, scheduling/admission,
  repositories/sources/runs, conflicts and review inspection.
- Notifications/preferences, account/profile/security/token flows, login,
  registration/reset/device/CLI authorization, administration and global navigation.

[The generated manifest](frontend-analytics-manifest.json) is the authoritative
source index: registered routes and their implementations, literal JSX sites,
conditional values, static navigation definitions and forwarding sites. Counts
are recomputed from the TypeScript AST; they are not quotas. Unique IDs are
semantic categories, JSX sites are source declarations, and forwarding sites
may have no literal ID until a caller opts in. A wrapper prop is not itself a
DOM element. Repeated resources/pages, desktop/mobile navigation and portal
instances can share an ID; neither IDs nor source sites count runtime elements,
unique users, pageviews, successful operations or exhaustive business coverage.

Development checks (from the repository root with web dependencies installed):

```sh
node web/scripts/analytics-manifest.mjs
# After reviewing intentional source changes:
node web/scripts/analytics-manifest.mjs --write
pnpm -C web test src/app/analytics-manifest.test.ts
```

Do not ship the manifest as a runtime closed enum. A deployment collector should
have its own small, explicit event/ID allowlist and update it deliberately.

### Original eight compatibility meanings

| ID | Meaning |
| --- | --- |
| `search.query.submit` | Search form submit; button and Enter converge here. |
| `skill.card.open` | Existing card activation; click or its direct Enter/Space handler. |
| `skill.detail.render` | Structural detail root after loading/error/missing/deleted guards; not a unique pageview or success. |
| `skill.download.request` | Package download attempt; authentication/guards may reject it. |
| `skill.command.copy` | Command-copy attempt for the displayed installation method; not clipboard or installation success. |
| `skill.prompt.copy` | Agent-prompt-copy attempt; not execution or installation. |
| `skill.publish.request` | Publish/update/resubmit attempt, never avatar-only save. |
| `skill.publish.confirm` | Explicit publish warning continuation, never all generic dialogs or avatar-only save. |

Installation method tabs distinguish SkillHub and ClawHub; the original command
copy ID remains compatible. Public/namespace/private choices and business modes
use static categories, not data fetched from the selected object.

## Native observation policy and limits

Attributes alone never create events. Choose the native event per host, and
count one semantic target per event rather than every marked ancestor:

- Forms: observe `submit`, not both submit-button click and submit. Enter and
  native validation follow existing app behavior. Rejected validation may produce
  no submit. Search typing/clear is not a search submission.
- Buttons/links: observe click, including browser-generated keyboard click. Ignore
  disabled, aria-disabled and data-disabled controls. An enabled click can still
  fail app guards; busy/authorization semantics remain the application's.
- Custom DIV cards with existing Enter/Space handlers need that keydown in addition
  to click. Native buttons must not count both. Tabs generate click from navigation
  keys; do not count Arrow/Home/End keydown again. Do not invent keyboard handlers
  for existing mouse-only rows or directories.
- Native select: observe change. Where the static marker is on an option, read only
  `selectedOptions[0].getAttribute('data-analytics-id')`, never option value/text.
  A select marker describes its context, not the raw choice. Avoid click/change
  double counting. Checkbox/radio change is an intent, not saved server state.
- Radix Select items are actual option DOM, sometimes in a portal. Pointerup or
  Enter/Space on an item is selection intent, not proof that onValueChange committed
  a new value. Opening the trigger and navigation/highlight are not committed
  selections. A click-only collector is insufficient. Validate event order with
  the deployed component/browser version rather than manufacturing app hooks.
  Trigger `*.interact` markers cover observable activation/keydown intent, including
  closed-trigger typeahead; never capture the typed key, infer the chosen value,
  or call that interaction a committed selection. Typeahead can change selection
  without opening an item. Keep trigger interaction and item selection-intent
  metrics separate instead of counting both as the same selection.
- File inputs: change means a selection was offered, not upload success. Drop is
  offered files, not accepted/validated files. Picker-opening click differs from
  change; ignore programmatic input clicks as additional user intent. Never read
  filenames, paths or file metadata. Picker cancellation is not observed.
- Crop surface pointer/keyboard interaction is adjustment intent, not proof the
  crop changed or an image upload succeeded; no coordinates or image data may leave.
- Explicit cancel/close buttons, including X, preserve workflow-specific meaning.
  Escape, overlay dismissal, route teardown, browser Back, automatic close and
  native picker cancel are not reliably observable through these static attrs.
  Closing an editor is not proof an in-flight request was aborted.

Use document-level delegation for portals; React ancestry is not DOM ancestry.
Wrapper forwarding is caller-scoped, including confirmation/cancel and close X;
generic UI primitives do not guess business IDs. Keep the nearest semantic target
and reject nested unmarked interactive controls rather than attributing them to
an ancestor card/form. Synthetic clicks (including temporary download anchors)
are not new successful user actions. Do not block or change default behavior,
propagation, focus, route history, permissions or app handlers.

`skill.detail.render` is not an automatic pageview event. SPA route classification,
render retries, consent transitions, duplicate initialization and view deduplication
are separate collector policies. Never derive these from full URLs/query strings.

### Privacy and integration modes

The recommended output allowlist is only `{ id, event }`, each validated against
operator-owned static categories. Do not collect textContent/innerHTML, input or
option values, clipboard contents, href/full URLs/query/fragment, cookies, storage,
identity/account IDs, device codes, tokens, invitation links, private coordinates,
filenames/paths, review/report reasons or crop data. Do not scrape arbitrary DOM
attributes or reuse test IDs (some intentionally contain resource identifiers).

Disabled integration means no injected script and zero analytics collection.
Enabled delivery does not itself authorize collection: initialize only after
applicable consent/policy approval, stop on withdrawal, and separately review any
outbound adapter and CSP connect destinations. External JS has page privileges;
serve reviewed versioned code, restrict access and treat compromise as a security
incident. Vendor credentials and ingestion endpoints belong outside core.

## Configure rules and add tags

[The vendor-neutral helper](examples/analytics/observe/v1/skillhub-intents.mjs)
exports `observeSkillhubIntents({ root, rules, onIntent })`, frozen `DEFAULT_RULES`
(the original 12 categories), and pure `resolveRules(rules)`:

```js
import { DEFAULT_RULES, resolveRules, observeSkillhubIntents } from './skillhub-intents.mjs'
const rules = { ...DEFAULT_RULES, 'skill.example.open': 'click' }
const { active, skipped } = resolveRules(rules) // Inspect locally; do not send config diagnostics.
const stop = observeSkillhubIntents({ root: document, rules: active, onIntent: sendIntent })
```

Omitting `rules` uses the defaults; passing it REPLACES them. Merge explicitly as
above. The example accepts the same grammar the product manifest enforces:
`^[a-z]+(?:\.[a-z]+){2,}$` (at least three dot-separated segments, lowercase letters
only; no digits or underscores). For example, `skill.command.skillhub.select`
can reuse `click`. Only finite rule types are supported:

- `click`: ordinary buttons/links. FORM, SELECT, TEXTAREA and non-button INPUT
  hosts are ignored (including file inputs).
- `submit`: FORM only; button click and Enter converge on native submit.
- `card`: DIV role=link with its existing Enter/Space handler, plus click. The
  observer does not implement keyboard activation or synthesize clicks.

Select/file/drop/crop and other special interactions need a future rule type;
never configure them as `click`. Unknown/unconfigured actions, invalid IDs,
non-string rules and unsupported rule types/hosts are skipped without affecting
the page. `resolveRules(...).skipped` gives `{ id, reason }` for invalid entries;
an invalid rules container yields an `id: null` diagnostic and an empty active map.
It validates configuration, not the live DOM host.

Importing the helper does nothing; without a callback it installs no listeners.
The same root returns the existing stop function and keeps the FIRST config and
callback. To change config, call `stop()` and initialize again. Nested observers
from this module instance share event deduplication; only the nearest marked host
is considered, never an ancestor behind an unmarked nested interactive control.
Native-button keydown, repeated/IME/modifier card keydown are ignored. Sync throws
and returned rejected promises from the callback are swallowed, with no retry or
queue. No network, cookies, storage, identity or route patching occurs in the
helper, and it never prevents defaults or stops propagation.

To add a normal action: product owners add a literal `data-analytics-id` on the
appropriate existing host, run `node web/scripts/analytics-manifest.mjs --write`,
review the manifest diff and test the host/native behavior. Deployers then add one
rule line. No product listener or SDK is needed. Download click is NOT download or
installation success; publish submission is NOT publish success.

The repository-integration test lives at `web/analytics-example.test.ts`, alongside
the existing root-level web tests. Vitest discovers it with `pnpm -C web test`;
run it alone with `pnpm -C web test analytics-example.test.ts`. It stays outside
the production TypeScript project's `src`/`test` inputs because the docs-only
example is not present in the `web/` Docker build context.

## Connect a platform: local Umami v3.4.1 demo

[The Compose example](examples/analytics/compose.yaml) runs Umami v3.4.1 (MIT),
Postgres 16 and an optional edge; SkillHub remains your existing web upstream.
From the repository root:

```sh
docker compose -f docs/examples/analytics/compose.yaml config
docker compose -f docs/examples/analytics/compose.yaml up -d
```

The default Web upstream is `host.docker.internal:80`, matching the release
Compose `WEB_PORT` default of 80. Override `SKILLHUB_WEB_UPSTREAM` with the actual
reachable host:port if different. The edge's `extra_hosts` maps this hostname to
`host-gateway` on native Linux; Docker Desktop/OrbStack already provide it. The
host Web listener must be reachable from containers. The edge inherits the standard
server-level `client_max_body_size 100M` for application proxying (100 MiB); it does
not reduce uploads to Nginx's default 1 MiB.

This creates a demo network and ephemeral database, not production storage. Use
an isolated local Docker environment with available network pools. Open
`http://localhost:8300` for local setup only. Umami's default `admin` / `umami`
credentials, the public DB password and app secret are DEMO ONLY: change them.
Create a synthetic website in Umami, copy its UUID into
[`observer.mjs`](examples/analytics/observe/v1/observer.mjs), then explicitly change
`enabled` and the consent hook for your local policy. Open SkillHub through
`http://localhost:8081` and exercise an enabled action; inspect the request to
`/_skillhub-umami/api/send` and Umami's event view. Tear down the local demo with
`docker compose -f docs/examples/analytics/compose.yaml down -v` (demo data is removed).

[`umami-adapter.mjs`](examples/analytics/observe/v1/umami-adapter.mjs) is the only
vendor-specific code. It loads `/script.js` with `data-auto-track="false"`, so
v3.4.1 installs no pageview, history hook, click listener or performance observer.
`data-host-url` is the same-origin `/_skillhub-umami` prefix, and
`data-before-send` names its sanitizer. Do not load another Umami tracker on the
same page (the SDK/global hook are singletons). It calls `umami.track(id, { event })`;
at most 20 valid intents wait in memory for up to three seconds while the script
loads (excess intents are dropped). They are released when ready, or dropped on
load failure, timeout or `stop()`. Once ready, every valid intent calls the tracker
independently: concurrent or hung requests do not block later intents. Each call's
wait is time-bounded; missing, blocked, throwing or rejecting trackers resolve
silently. There is no retry, durable queue or post-load backlog; best-effort counts
can undercount. After `stop()` returns, no new request is dispatched; only a
request whose fetch already started cannot be recalled (nor cancelled by a timeout).
The before-send hook keeps its immediate stop check and adds a non-enumerable
`toJSON` guard that re-checks consent when the SDK serializes at dispatch. In pinned
v3.4.1 this runs synchronously in the fetch expression inside the SDK's try/catch:
throwing on stop prevents fetch and is swallowed by the SDK. This relies on that
ordering; a different Umami version must re-verify it with the dispatch-boundary
regression tests in `web/analytics-umami-example.test.ts`.

The sanitizer allowlists the FINAL SDK payload, not just the callback. Only
`{ type: 'event', payload: { website, name, data: { event } } }` survives; website
must match configuration, name must match the ID shape (and Umami's 50-character
limit), event must be click/submit/keydown. It returns null for pageviews (including
unnamed `event` payloads), identify, performance and everything else. Hostname, URL,
title, referrer, screen, language, tag, identity and extra data keys are removed.
Values are read once, and only validated primitives are forwarded.
The SDK still reads title/URL/referrer in-page before sanitizing; it also reads
localStorage for its own `umami.disabled` switch. Those are SDK behaviors, not
behaviors of the vendor-neutral helper.

This is NOT anonymous transport or zero server processing: IP, User-Agent and
Origin still leave the browser. Umami sends `x-umami-website-id`,
`x-umami-hostname` (the actual hostname) and, after a response, `x-umami-cache`
(a session cache token). They are NOT sanitized JSON. The edge tightens UI
Referrer-Policy to `no-referrer` and strips Referer, Cookie and Authorization on
collector proxying; otherwise same-origin Referer can expose paths/queries.
Umami v3.4.1 hashes website/IP/User-Agent/salt into a server-side session and derives
browser/OS/device/geo from transport metadata. Removing JSON screen/language does
not disable these derivations; review access logs, retention, consent and local
law. Its missing-URL fallback stores empty url_path and fallback hostname
`localhost`, not the current page; url_query is an empty string (not SQL NULL in
v3.4.1), while title/referrer/screen/language stay null.

To replace the platform, replace the adapter and edge ingestion endpoints, keeping
the helper and static payload contract. No admin configuration page, SkillHub
analytics DB, persistent queue or multi-vendor SDK is introduced.

## Turn collection off

No injection means zero collection. `enabled: false` in the deployer's entry is
also inert: no helper initialization or tracker load. On consent withdrawal call
`window.skillhubAnalytics.stop()`; it removes listeners and denies SDK payloads,
including those awaiting dispatch. After it returns, no new request is dispatched;
only a request whose fetch already started cannot be recalled. The dispatch guard
relies on pinned v3.4.1 serializing at dispatch; re-run the adapter regression tests
to re-verify this ordering for a different Umami version. Resume only after approval
via `start()`. Platform-side disabling/deleting the website, Umami's local
`umami.disabled` switch, or blocking the two ingestion routes are additional
operator controls, not substitutes for consent. Removing injection and purging
HTML is the deployment rollback; default SkillHub never loads any of this.

## Optional deployment-owned Nginx injection

Prefer a separately maintained same-origin HTTPS module, optionally inserted by
Nginx `sub_filter`, not an app entrypoint modification. Start with
[the opt-in edge template](examples/analytics/nginx.conf). It is not loaded by the
product. Compose substitutes only the two upstream host:port variables. Publish
its HTTP listener on loopback behind your HTTPS terminator. Serve the supplied
`observer.mjs`, helper and adapter outside the app bundle; only enable the entry
after configuring website/rules/consent.

The sample filters only the UI fallback's default `text/html` type, once per
response, inserting a module before `</head>`. Module loading is deferred and no
application code depends on it; missing/failed JS must not stop SkillHub. The
versioned prefix location overrides SPA fallback, sets JavaScript MIME and nosniff,
and uses `try_files $uri =404` with directory listing off. The served directory
contains only the three JS modules, never `.d.mts` or config. Missing modules are
404, never index.html. Code is immutable: deploy changed bytes/config under a new
version directory and update the injection path (breaking contracts need a new
module version). The two exact Umami routes expose tracker and send only; all
other paths under that prefix return 404. Same-origin script-src/connect-src are
sufficient for this demo, subject to the existing policy below.

Deployment checklist:

1. Check `nginx -V` for http_sub_module (built with `--with-http_sub_module` or an
   equivalent loaded module), then `nginx -t`. Module absence is a blocker, not an
   invitation to weaken security. Review actual final config with `nginx -T`.
2. Upstream `Accept-Encoding ""` requests uncompressed HTML. Ensure upstream
   gzip_static/Brotli/CDN compression cannot still send encoded bytes. Filtering
   compressed input does not work; downstream compression may run after filtering.
   Leave API, auth, download, asset and runtime-config locations unmodified.
3. Filtered HTML must not reuse unfiltered validators or cached 304 responses. The
   example removes upstream ETag/Last-Modified/Content-MD5 and cache headers,
   suppresses conditional requests and disables proxy cache, serving no-store HTML.
   Purge old edge/CDN HTML on enable/rollback; test GET/HEAD, conditional headers,
   existing browser caches and 200/304 behavior. Do not cache injected HTML under
   the old unfiltered entity validator. API and immutable assets retain their policy.
4. Preserve existing CSP and all security headers. The script must satisfy the
   current script-src policy (same-origin alone does not satisfy nonce-only or
   strict-dynamic policies). A per-response nonce or approved external hash/SRI
   policy needs deployment-specific coordination; otherwise do not enable injection.
   Never add unsafe-inline/unsafe-eval or broad wildcards. Any reporter needs an
   explicitly permitted connect-src endpoint; this example uses same-origin only.
   Nginx add_header inheritance is location-sensitive: merge/repeat existing edge
   security headers as necessary; do not drop or hide upstream CSP to make it work.
5. Test actual built SkillHub HTML, not just a synthetic HTML fixture: exactly one
   script for UI routes, none for APIs/assets/downloads/auth endpoints; script MIME,
   missing-script 404, root/deep links, compression, CSP, disabled delivery and
   collector failure. Then browser-check native events, portals, guards and consent.
   A fixture filter probe is not final-config, SkillHub or production acceptance.

### Supported sub-path mapping

The sample is root deployment. For a supported `/skillhub/` deployment, keep that
prefix on the edge-to-app proxy URI (proxy_pass without a trailing slash), and
configure the app normally with SKILLHUB_WEB_BASE_PATH=/skillhub/ and matching
same-origin API base. Its existing container entrypoint owns internal prefix
rewriting; do not rewrite it a second time at the edge.

Adapt the sample's UI `location /` to `location /skillhub/`; add an exact
`/skillhub` redirect to `/skillhub/`; scope the bypass regex to
`^/skillhub/(api/|oauth2/|login/oauth2/|\.well-known/|assets/|registry/|runtime-config\.js$|nginx-health)`.
Change the versioned module prefix location and injected src to
`/skillhub/_skillhub-observe/v1/` and
`/skillhub/_skillhub-observe/v1/observer.mjs`, plus the matching deny prefix.
With `root /srv`, install the three JS files under
`/srv/skillhub/_skillhub-observe/v1/`; relative imports follow automatically.
Also scope BOTH exact Umami endpoints and the deny prefix to
`/skillhub/_skillhub-umami/`, and change the entry's adapter `prefix` accordingly;
upstream `/script.js` and `/api/send` stay unchanged. Keep other site locations
unchanged; do not put sub_filter at http/server scope. Use any supported validated
base path consistently, preserving original request URIs. HTTPS/public registry,
OAuth callbacks and generated asset URLs still need the normal deployment setup.

Rollback: remove the sub_filter directives/injection location customization, test
configuration, reload through the normal deployment procedure, and purge cached
HTML. Existing cached versioned JS can remain temporarily but must not be referenced
or initialized. Verify new HTML has no script, no callback/reporting occurs and
SkillHub remains usable. This document does not authorize production changes.
