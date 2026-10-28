## Why

Self-hosted deployments need vendor-neutral business analytics using the official image and deployment configuration, without a fork or a bespoke webhook protocol. Issue #1009 and maintainer comment 6463066865 authorize existing domain events → dedicated OpenTelemetry Logs → OTLP → operator receiver.

## What Changes

- Add a default-off, independent OTLP/HTTP protobuf business Logs adapter in app.
- Snapshot occurrence time, opaque actor, namespace ID and visibility at existing domain producers; keep notification/search semantics and permissions unchanged.
- Include publication (all four producers), published-download preparation, content-review decisions, promotion decisions, archive/unarchive, effective visibility change and version yank.
- Bound admission, attributes, retry, timeout and shutdown; expose Micrometer analytics-export health counters.
- Document event semantics, deployment, data boundary, real receiver/query example and acceptance evidence alongside implementation.

## Capabilities

### New Capabilities
- `business-event-logs`: operator-controlled, lossy business analytics using OTLP Logs.

### Modified Capabilities
None. No API, package, CLI, storage schema, authorization or audit contract changes.

## Scope and deferrals

Not audit, billing, notification, outbox, replay, historical backfill or exactly-once delivery. No application-log bridge, new trace pipeline, vendor SDK or BOM upgrade. Metadata/avatar review decisions without existing decision events, hide/unhide, promotion revocation, review submission/withdrawal, Suite events, search/views, stream completion and installation are deferred. Effective metadata-review visibility changes ARE included through their existing visibility event. No rejection comments, content, names/slugs, network identifiers or request headers are exported.
