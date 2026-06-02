# LibreChat SkillHub Hermes V1 Contract Fixtures

These fixtures are the Phase 0 source of truth for cross-repo contract tests. They are still documentation fixtures until each participating repo wires a real validation command against this directory.

Consumers:

- SkillHub validates resource IDs, internal endpoint envelopes, artifact fingerprints, token rejection behavior, lifecycle errors, and exact-byte body hashes.
- Control Tower validates inventory records, batch decisions, decision-token claims, asymmetric signing, and runtime envelope entries.
- LibreChat validates decision responses, profile pins, internal API request/response handling, and disabled states.
- Hermes validates pinned profile records, artifact fingerprints, sync metadata, and runtime envelope entries.

Fixture clock for token validation tests:

```text
2026-05-29T10:16:00Z
```

Fixture signing material is intentionally embedded for local contract tests only in `decision-token.allow.json`. Production keys must be asymmetric, must be rotated by Control Tower, and private keys must never be present in SkillHub, LibreChat, Hermes, logs, or repository configuration.

Token and body-hash fixtures use exact `bodyUtf8` strings. Consumers must hash those bytes directly and must not reserialize the parsed `body` object.

Traceability:

| Fixture | Primary Contract Coverage |
| --- | --- |
| `resource-ids.*.json` | Resource grammar and invalid integrated versions. |
| `capability-inventory.records.json` | Namespace/container/version/review-task inventory and lifecycle deltas. |
| `decision-token.*.json` | Control Tower decision-token signing, claim binding, and rejection matrix. |
| `internal-api.requests-responses.json` | SkillHub internal endpoint request, response, header, idempotency, and pagination shapes. |
| `internal-api.errors.json` | Shared error envelope and fail-closed status mapping. |
| `profile-pin.records.json` | LibreChat pin persistence and Hermes sync diagnostics. |
| `runtime-envelope.requested-skills.json` | Runtime `skill.invoke` envelope shape and local-present denial case. |

Download fixtures deliberately use one-time internal bundle handles instead of raw streams or pre-signed URLs. Consumers must treat `oneTimeInternalHandle` values like secrets: do not log them, do not persist them beyond the response TTL, bind them to the returned artifact fingerprint, and consume them once over internal mTLS/service-authenticated transport.

Protected V1 endpoints carry semantic parameters in request bodies. The `wrong_query_hash` rejection case exists to keep validators honest for future query-bound endpoints; current positive protected endpoint fixtures use `bodySha256`.
