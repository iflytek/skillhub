# CLI Namespace Coordinates and Structured Errors Design

## Context and approval

GitHub issue #606 reports two coupled CLI 0.1.9 failures: namespaced install
coordinates can silently resolve against `global`, and JSON API responses with
HTTP 403 are always rewritten as a token-scope error. The Multica issue's
technical-analysis comment defines the desired normalization, conflict, error,
documentation, and package-verification behavior. The project manager then
assigned implementation against that design on `fix/cli-namespace-errors`, so
that comment and assignment are the approved design baseline.

## Considered approaches

1. Centralize coordinate normalization and structured response errors in the
   existing shared parser and client. This is the selected approach because all
   commands receive one interpretation and tests can exercise the public
   contract without duplicating parsing or status handling.
2. Patch `install` only. This would be smaller, but `remove --remote` already
   consumes the same parser and would retain inconsistent behavior.
3. Change the server or documentation to accept only `--namespace`. This would
   preserve the CLI bug and contradict documented coordinate forms.

## Coordinate contract

The CLI accepts these equivalent inputs:

| Input | Namespace | Slug |
|---|---|---|
| `my-skill` | `global` | `my-skill` |
| `team/my-skill` | `team` | `my-skill` |
| `@team/my-skill` | `team` | `my-skill` |
| `team--my-skill` | `team` | `my-skill` |
| `my-skill --namespace team` | `team` | `my-skill` |

The command parser must not inject `global` before coordinate normalization.
`global` is applied only when the input is a bare slug and no explicit
`--namespace` is supplied. If a coordinate and `--namespace` name the same
namespace, the input is accepted. If they differ, the command fails with a
usage error instead of silently choosing either value.

Structurally incomplete coordinates such as an empty string, `@team`,
`team/`, `/my-skill`, `--my-skill`, and `team--` fail with a usage error. The
normalizer does not add new namespace or slug character restrictions; server
validation remains authoritative for those rules.

## Error contract

For unsuccessful JSON API responses, the client reads the body once and only
uses the documented public fields `msg` and `requestId` when they are non-empty
strings. A server `msg` becomes the `CliError` message. A `requestId` is stored
in error details and rendered in both JSON and human-readable CLI output.

Exit classification remains stable:

- 401 and 403 use the authentication exit code.
- 404 and other application failures use the generic exit code.
- 502 and 503 use the network exit code.

When `msg` is absent, invalid, or the body is not JSON, the CLI uses a status-
specific fallback. In particular, the 403 fallback is `access denied` and does
not speculate about token scope. Raw non-JSON bodies and unrecognized fields
are not surfaced, avoiding disclosure of internal response content. Download
responses use the same structured error extraction while retaining their
download-specific fallbacks.

## Components and data flow

- `cli/src/shared/skill-name-parser.ts` parses and resolves coordinates,
  including explicit namespace conflict detection.
- `cli/src/commands/install.ts` and `cli/src/commands/remove.ts` consume the
  resolved coordinate.
- `cli/src/index.ts` leaves `--namespace` unset unless the caller supplies it.
- `cli/src/clients/skillhub-client.ts` converts unsuccessful responses into
  structured `CliError` instances.
- `cli/src/shared/output.ts` renders `requestId` for human users; JSON output
  already serializes error details.
- `cli/src/commands/help.ts`, `cli/README.md`, and `cli/CHANGELOG.md` document
  supported forms, conflicts, and the 403 behavior change.

## Testing and package verification

Unit tests cover the coordinate matrix, malformed inputs, matching/conflicting
`--namespace`, structured and unstructured 401/403/404/500/502 responses, and
human request-ID rendering. An integration install test executes the real CLI
argument parser against a fake registry so the former `default: 'global'`
override cannot regress.

The release check builds and packs the CLI, inspects the tarball file list, and
runs the packed executable for version/help plus focused coordinate/error smoke
tests. The published npm 0.1.9 package is retained only as a comparison
artifact; no package publication or main-branch merge is part of this work.

## Risks

- Rejecting ambiguous coordinate/flag combinations is an intentional behavior
  tightening and is called out in release notes.
- Server `msg` is treated as the public localized message defined by the API
  envelope. Raw body content is deliberately not exposed.
- This change does not publish a new npm version; release owners must verify the
  future dist-tag after the approved PR is merged and released.
