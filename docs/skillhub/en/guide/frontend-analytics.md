# Frontend analytics

Collection and SDK loading are off by default. The delivered example is a **manual integration**; configuration-only activation through Docker/Compose or Helm has not been merged. Click events express intent, not successful downloads, publishing, or execution.

## Connect a platform

1. **Choose a platform**: reuse one or run the isolated Umami demo. Never use demo credentials in production.
2. **Configure collection**: provide a valid website UUID, selected actions, and an explicit consent check. Deployment owners maintain the entry module, adapter, and proxy.
3. **Verify before enabling access**: check outgoing fields and stored events, then confirm disabled collection and platform failure leave normal business flows working.

The [detailed setup guide (Chinese)](./frontend-analytics-guide.md) contains commands, rules, sub-paths, and recovery steps. The [interface and privacy contract (English)](./frontend-analytics-reference.md) defines event and adapter boundaries.

## Acceptance checks

| Scenario | Expected result |
| --- | --- |
| Disabled by default | No analytics SDK or events; business flows work |
| Enabled with consent | One corresponding event per action, actually stored; no search terms or identity fields |
| Platform fails while enabled | Collection fails, but search and skill pages still work |
| Platform recovers | Refresh to verify new stored events; lost events are not guaranteed to replay |

## Common questions

- **No data?** Check the demo edge entry, consent, website UUID, rules, and cache. HTTP 200 alone does not prove storage.
- **Can any platform work by setting its URL?** No. Other platforms need an adapter and reviewed proxy endpoints.
- **Is collection anonymous?** A small JSON payload is not anonymous transport. IP and User-Agent may still reach the platform. Deployment owners handle access control, retention, and compliance.

## Disable collection

Use `window.skillhubAnalytics.stop()` to stop future dispatch in the current page; already dispatched requests cannot be recalled. For persistent disabling, restore `enabled: false`, recreate edge, and refresh with cache disabled. Remove the injection/edge and return to the original Web entry to remove the integration entirely. Follow the detailed guide to clean up only the isolated demo, never shared platform or business data.
