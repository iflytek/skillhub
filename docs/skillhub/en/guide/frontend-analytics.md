# Frontend analytics

SkillHub does not collect frontend actions or load an analytics SDK by default. Pages expose passive semantic attributes; deployment owners choose an integration under their consent and privacy policies.

The delivered example is a manual integration: the deployment owner maintains the collector entry, adapter, and proxy configuration. Configuration-only activation and lazy loading through Docker/Compose or Helm remain follow-up work, not current deployment instructions.

- [English interface and privacy contract](https://github.com/iflytek/skillhub/blob/main/docs/frontend-analytics.md): event meaning, adapter boundaries, transport fields, and sub-path mapping.
- [Step-by-step integration guide (Chinese)](https://github.com/iflytek/skillhub/blob/main/docs/frontend-analytics-integration.md): reuse a platform or run an isolated Umami demo, configure rules, verify stored events, separately test disabled collection and enabled dependency failure, then recover or clean up.

A download click expresses intent, not successful download, installation, or execution. Keep administration secrets out of the browser. Platform retention, access control, and compliance belong to the deployment owner. These links follow repository main; pinned deployments should use documentation and examples from the matching tag.
