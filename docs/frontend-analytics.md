# Passive frontend analytics contract

For deployment, use the [Chinese configuration guide](frontend-analytics-integration.md).
Docker/Compose and Helm ship optional analytics with Web. No configuration means no
controller/adapter chunk, SDK or collection request. The small configuration gate is
part of bootstrap; the controller and SDK are not. Business components remain passive.

## Contract and scope

`data-analytics-id` names an action category, not a unique event, resource or user.
It describes intent, not its result. IDs match `^[a-z]+(?:\.[a-z]+){2,}$`.
Lowercase dotted names are independent of language, visible text and layout.
Published meanings are frozen. A meaning change requires a new ID. Renames/removals
need release notes and a reviewed [manifest](frontend-analytics-manifest.json) diff.
The manifest is an AST source inventory, not a runtime closed enum or a count of
users, pageviews, successful operations or exhaustive business coverage.

The inventory includes landing/search, skills, suites, publishing, review,
governance, namespaces, GitHub integration, notifications, preferences, account,
authentication, administration and navigation. Literal branches distinguish finite
actions. Never interpolate resource, slug, version, user, provider, role or backend
values into IDs. Fixed navigation metadata is source-owned. Forwarding sites do
not necessarily represent DOM elements; portals and repeated resources share IDs.

Run from the repository root with dependencies already installed:

```sh
node web/scripts/analytics-manifest.mjs
# Only after reviewing intentional source changes:
node web/scripts/analytics-manifest.mjs --write
pnpm -C web test src/app/analytics-manifest.test.ts src/features/analytics
```

### Original eight compatibility meanings

| ID | Meaning |
| --- | --- |
| search.query.submit | Native search form submission, not typing or search success. |
| skill.card.open | Existing card click or direct Enter/Space activation. |
| skill.detail.render | Structural loaded detail root, not an automatic pageview. |
| skill.download.request | Download attempt; guards may reject it. |
| skill.command.copy | Command-copy attempt, not clipboard or installation success. |
| skill.prompt.copy | Prompt-copy attempt, not execution or installation success. |
| skill.publish.request | Publish/update/resubmit attempt, not avatar-only save. |
| skill.publish.confirm | Explicit publish warning continuation, not every dialog. |

Installation-method tabs distinguish SkillHub and ClawHub without changing the
original command-copy meaning. Visibility choices and business modes use static
categories, not data fetched from the selected object.

## Native observation policy and limits

Attributes alone do not create events. Keep one nearest semantic target per event.
Never change defaults, propagation, focus, history, permissions or app handlers.

- FORM uses submit, not submit-button click plus submit. Enter converges here;
  rejected native validation may produce no submit. Typing/clear is not submission.
- Ordinary buttons/links use click, including browser-generated keyboard click.
  Ignore disabled, aria-disabled and data-disabled controls. App guards still apply.
- DIV role=link cards with existing Enter/Space handlers also use keydown. Do not
  synthesize activation. Ignore repeat, IME and modifier keydown. Native buttons
  must not count keydown and their generated click twice.
- Tabs generate click from navigation keys; do not count Arrow/Home/End again.
  Do not invent keyboard support for existing mouse-only rows/directories.
- Native selects, checkboxes and radio changes have different boundaries. An
  option marker can describe static selection intent, never its value/text. Do
  not infer committed selection from opening a trigger or highlighting an item.
- Radix selection may use pointerup or Enter/Space in a portal. Trigger typeahead
  can change selection without opening an item. Trigger interaction is not proof
  of committed selection; never capture typed keys. Current finite rules do not
  implement select/file/drop/crop; do not misconfigure them as click.
- File selection and dropping only offer files, not upload success. Do not read
  filename/path/metadata. Programmatic picker clicks are not extra user actions;
  cancellation is not observed. Crop interaction is not proof of changed pixels.
- Explicit cancel/close retains workflow meaning. Escape, overlay, route teardown,
  browser Back, automatic close and native picker cancel are not reliably observed
  by these markers. Closing is not proof a pending request was aborted.

Use document delegation for portals; React ancestry is not DOM ancestry. Wrappers
forward caller-scoped IDs, not guessed business IDs. Reject nested unmarked
interactive controls rather than attributing them to an ancestor card/form.
Synthetic temporary download anchors do not prove new successful user actions.
SPA classification, consent transitions and view deduplication are separate
policies; never derive them from full URLs/query strings.

## Single product source and interfaces

The sole helper is `web/src/features/analytics/skillhub-intents.mjs`:

- `observeSkillhubIntents({ root, rules, onIntent }) -> stop`.
- Frozen `DEFAULT_RULES` has the original twelve rules listed in the guide.
- `resolveRules(rules) -> { active, skipped: [{ id, reason }] }` is pure. Invalid
  containers produce id:null and no active rules. Unknown IDs are not rejected
  merely for being absent from the manifest. Invalid grammar/types are skipped.
- Omitted rules use defaults. Explicit rules replace defaults; {} collects nothing.
- click excludes FORM/SELECT/TEXTAREA/non-button INPUT; submit is FORM-only; card
  is DIV role=link only. Host validity is checked at observation, not resolution.
- Intent is exactly `{ id, event }`; event is click/submit/keydown. Same-version
  evolution is additive optional allowlisted static fields; consumers ignore
  unknown keys. Breaking changes need a new module contract.
- Importing does nothing; without callback no listeners are installed. Same root
  returns the same stop and retains the FIRST rules/callback. Stop then initialize
  to change configuration. Old disposers cannot remove a new installation. Stop
  is idempotent; nested observers share event deduplication.
- Callback throws/rejections are swallowed without affecting business events.
  The helper itself has no network, cookies, storage, identity or history hooks.

The controller owns authorization, rule resolution, generations and lifecycle.
Static registry metadata validates public adapterConfig; factories lazily import
adapters. Factory cancellation prevents late imports from creating an SDK after
stop. Adapter exposes `send(intent) -> Promise<void>` (never rejects) and stop().
The loader runs separately after the first React DOM commit, never blocking mount.
Disabled, absent, invalid or denied configuration does not import the controller.
Public `window.skillhubAnalytics.start()` explicitly grants current-page consent;
stop() immediately disables observation. Enabled=false cannot be overridden by
start. Deployment changes require recreating/restarting Web; old tabs do not update.

## Configuration and proxy

`SKILLHUB_WEB_ANALYTICS_CONFIG` is JSON with enabled (boolean/default false),
adapter (static registry name), consent (denied/granted/default denied), rules
(optional finite rule map), adapterConfig (adapter-owned public object). Umami
accepts only websiteId UUID. Invalid configuration disables only analytics with
field-only diagnostics, never raw values. Rule resolution keeps its skip behavior.
The startup parser limits JSON to 64 KiB/32 levels and rejects duplicate keys.

`SKILLHUB_WEB_ANALYTICS_UPSTREAM` is a deploy-only HTTP(S) origin: no credentials,
path, query, fragment, whitespace or injection characters. It is never put in
browser configuration. Existing BusyBox tools validate deployment control fields;
base64 transports JSON safely as data into the existing no-store runtime-config.js.
No jq, Node runtime, extra package, CSP relaxation or HTML injection is required.

The reserved same-origin prefix is `/_skillhub-analytics/`. BASE_PATH is applied in
the browser; existing Nginx rewrite strips it once. Enabled configurations generate
exact script.js and api/send routes, with method restrictions; all other paths are
404, never Umami admin. Consent-denied enabled configurations retain those routes
for explicit dynamic start, but do not load/request them automatically.
Variable proxy_pass uses the existing resolver at request time. HTTPS verifies
certificates using the system trust bundle and correct SNI. Collector proxying
strips Cookie/Authorization/Referer and hides Set-Cookie. API/auth routes, 100M
uploads, TLS, business caching and permissions are not changed.

## Privacy and integration modes

Allowlist only static id/event, not text/HTML, input/option values, clipboard,
URLs/query/fragment, cookies, storage, identities, codes/tokens, private paths,
filenames, review reasons or crop data. Test IDs can contain resource identifiers;
do not scrape them. The manifest is not a payload.

Umami 3.4.1 uses manual-only script attributes and a final before-send sanitizer.
Only `{type:'event',payload:{website,name,data:{event}}}` survives; website must
match configuration, name must match product grammar and Umami's 50-character
limit. Pageview/identify/performance/context/extra data are dropped. Payload values
are read once. A non-enumerable toJSON guard rechecks stop synchronously at fetch
dispatch; upgrading the SDK must reverify its ordering with dispatch-boundary tests.
Stop cannot recall a fetch already started.

At most 20 valid intents wait in memory for up to three seconds while the SDK
loads. Excess, failure, timeout or stop drops them. Once ready, requests are
independent and time-bounded, with no retry/durable queue/post-load backlog.
Do not load a second Umami tracker. Missing/blocked/offline collectors never reject
into the application. SDK loading is trusted code with page privileges: review and
pin its version, access and retention; it is not an arbitrary remote-JS plugin.

This is NOT anonymous transport. IP/User-Agent/Origin still reach the platform.
The SDK sends website, actual hostname and session cache headers, reads
URL/title/referrer before sanitization, and reads its own localStorage switch.
Nginx strips sensitive headers before the collector, not before the browser's
request reaches Web. Web logs require their own privacy policy. Umami can derive
browser/OS/device/geo/session from transport metadata. With pinned 3.4.1 absent URL
stores empty path/query and fallback hostname localhost; title/referrer/screen/
language/distinct identity remain absent. Removing JSON fields does not disable
transport-derived processing. Operators must review consent, logs, access and law.

Supported-platform deployment, initialization, verification, upgrade, shutdown and
custom adapter development are documented in the guide. Custom adapters require
reviewed code, registry validation, cancellation, final payload cleaning and exact
proxy routes; business components must remain vendor-neutral and passive.
