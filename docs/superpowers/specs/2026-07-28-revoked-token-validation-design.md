# Revoked API Token Validation Design

## Goal

Prove and preserve fail-closed API-token behavior across the CLI API using a
real persisted token lifecycle. Invalid Bearer credentials must return HTTP
401 before endpoint business logic runs, including when a valid Web Session is
also present. A valid Bearer credential overrides the Session identity. When
Bearer is absent or the Authorization scheme is unsupported, the existing Web
Session identity is preserved; without a valid Session, public reads remain
anonymous and `whoami` returns 401. Valid credentials without sufficient
authorization continue to return HTTP 403.

## Scope

This change covers the following CLI routes:

- `GET /api/cli/v1/auth/whoami`
- `GET /api/cli/v1/skills/search`
- `GET /api/cli/v1/skills/{namespace}/{slug}/resolve`
- `GET /api/cli/v1/skills/{namespace}/{slug}/download`
- `GET /api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download`

It also covers the authenticated-versus-forbidden boundary on the affected
restricted read routes. An existing scope-protected CLI route may provide
supplementary scope-filter evidence only. This change does not add endpoints,
change runtime response fields, change token storage, add a database migration,
or change anonymous resource visibility rules. The OpenAPI correction marks
the already-nullable `whoami.email` value accurately without changing its JSON
field presence.

## Current-State Finding

The fail-closed implementation from closed PR #511 was later included in the
single replacement PR #523 and is present in both v0.2.14 and current `main`.
`ApiTokenAuthenticationFilter` already validates Bearer credentials before
business logic and rejects empty, malformed, unknown, expired, revoked,
missing-user, and disabled-user credentials through the configured
`AuthenticationEntryPoint`.

The verified repository gap is regression coverage, not a demonstrated
production-code gap. Existing tests separately prove token lifecycle
validation and invalid-Bearer filtering, but they do not exercise persisted
token creation, revocation, and all affected CLI endpoints in one integrated
matrix. The CLI API table in `docs/03-authentication-design.md` also retains
legacy paths, and there is no dedicated OpenAPI 3.0 authentication contract in
`docs/api/`.

The reported v0.2.14 runtime behavior still contradicts the source and test
evidence. Source equality alone does not establish which artifact or replica
served the reported requests. The defect therefore remains open until the
release artifact and affected runtime are identified and the same token
lifecycle is replayed against that identified runtime.

## Release Artifact and Runtime Identity Gate

Runtime verification is a required investigation track, not an optional
deployment check. Before interpreting a runtime result, record all of the
following for every server replica that may receive the request:

1. The configured deployment version and resolved image reference from the
   runtime environment and `docker compose config --images`.
2. The running container's image ID and registry `RepoDigest` from
   `docker inspect` / `docker image inspect`.
3. The OCI `org.opencontainers.image.revision` and
   `org.opencontainers.image.version` labels. The publish workflow generates
   these labels and also publishes a `sha-<short-sha>` tag, so the revision can
   be mapped back to a repository commit.
4. The externally observed application URL, health result, deployment profile,
   and request IDs for the authentication probes.

If the revision label is absent, the image digest must be mapped to the
corresponding publish-images workflow output or registry manifest. A mutable
tag such as `latest` or `v0.2.14` is not sufficient identity evidence by
itself. If neither a revision nor a digest-to-build mapping can be obtained,
the source/runtime contradiction is unresolved and the defect cannot be
closed.

Using a dedicated test user and non-production token, replay one lifecycle
against the identified running image:

1. Issue the token and call every matrix endpoint while it is valid.
2. Revoke that same token through the normal product flow and verify its
   persisted `revoked_at` value without exposing the raw token.
3. Reuse the same raw token against every matrix endpoint and capture status,
   response envelope, request ID, timestamp, and serving replica when
   available.
4. Repeat or pin requests per replica when a load balancer can route to mixed
   versions, and compare the image digest/revision of each replica.

If production mutation is not authorized, run the exact identified digest in
an approved isolated environment with equivalent auth/proxy configuration and
record that limitation. This does not by itself close the original field
report: an authorized runtime replay or owner-provided equivalent evidence is
still required.

The contradiction is closed only when source commit, published image digest,
running instance identity, and replay result form one consistent chain. A
mismatched digest indicates deployment drift; identical application images
with divergent behavior require investigation of proxy header forwarding,
mixed replicas, session/cookie contamination, and request routing before any
source-code conclusion is accepted.

## Architecture

`ApiTokenAuthenticationFilter` remains the single Bearer-authentication entry
point. Spring Security loads an existing Web Session identity before the token
filter runs. A valid Bearer token replaces that identity; an invalid, empty, or
malformed Bearer attempt clears it and returns 401. The filter ignores Basic
and other non-Bearer schemes, preserving the loaded Session identity. If no
Session exists, those schemes reach public reads anonymously and `whoami`
returns 401. Controllers must not duplicate token parsing, Session resolution,
or lifecycle checks.

The regression test will boot the Spring application with MockMvc, real
`ApiTokenService`, real `ApiTokenRepository`, and real user persistence. CLI
endpoint business services may be mocked only to make successful public-read
responses deterministic; authentication and token lifecycle components remain
real. This isolates the contract boundary under test: a rejected credential
must stop in the security chain before controller business logic executes.

The restricted-read authorization test is separate and must not mock the
permission decision. It will persist a PRIVATE or NAMESPACE_ONLY skill owned by
another user, authenticate a valid outsider token with no qualifying namespace
role, and exercise the real `CliSkillAppService` plus domain query/download
authorization path. At least `resolve`, latest download, and versioned download
must return HTTP 403. A DELETE request with a missing token scope may supplement
this check, but cannot replace any affected read-path assertion.

Production authentication code will be changed only when a new regression
test fails for the expected behavioral reason. Any fix must be the smallest
change at the shared authentication or token-validation source of the failure.
Endpoint-specific authentication patches and unrelated refactoring are out of
scope.

## Persisted Token Lifecycle

The test fixture creates an active user and issues a token through
`ApiTokenService`, retaining only the raw token returned at creation time.
Lifecycle transitions use production persistence paths:

1. Call an affected endpoint with the valid raw token and confirm successful
   authentication.
2. Revoke the token through `ApiTokenService.revokeToken`.
3. Call every affected endpoint with the same raw token.
4. Assert HTTP 401 and confirm protected endpoint business logic was not
   reached.

Expired-token coverage persists a token with an expiration timestamp earlier
than the service clock, then validates it through the same filter and
repository path. Unknown and malformed tokens exercise the same HTTP security
chain without creating a token row.

## Behavioral Matrix

The authentication rows use deterministic public fixtures. Latest and
versioned downloads are independent endpoints and must have independent test
arguments and assertions for every credential state.

| Credential state | `whoami` | Public `search` | Public `resolve` | Public latest download | Public versioned download | Meaning |
|---|---:|---:|---:|---:|---:|---|
| No `Authorization` header | 401 | 200 | 200 | Existing 200/302 success | Existing 200/302 success | Anonymous access is preserved only where already public |
| Basic or another non-Bearer scheme | 401 | 200 | 200 | Existing 200/302 success | Existing 200/302 success | Unsupported schemes are not treated as API-token attempts |
| Valid Web Session, no `Authorization` header | 200 as Session user | 200 as Session user | 200 as Session user | Existing 200/302 as Session user | Existing 200/302 as Session user | Existing browser identity is preserved |
| Valid Web Session + Basic | 200 as Session user | 200 as Session user | 200 as Session user | Existing 200/302 as Session user | Existing 200/302 as Session user | Non-Bearer schemes do not erase Session identity |
| Valid active token | 200 | 200 | 200 | Existing 200/302 success | Existing 200/302 success | Principal and roles/scopes are projected |
| Valid Web Session + valid active token | 200 as token user | 200 as token user | 200 as token user | Existing 200/302 as token user | Existing 200/302 as token user | Bearer identity overrides Session identity |
| Valid Web Session + revoked token | 401 | 401 | 401 | 401 | 401 | Credential cannot fall back to Session or anonymous |
| Valid Web Session + expired token | 401 | 401 | 401 | 401 | 401 | Credential cannot fall back to Session or anonymous |
| Valid Web Session + unknown token | 401 | 401 | 401 | 401 | 401 | Credential cannot fall back to Session or anonymous |
| Valid Web Session + empty Bearer credential | 401 | 401 | 401 | 401 | 401 | Empty authentication attempt is rejected before business logic |
| Valid Web Session + malformed Bearer credential | 401 | 401 | 401 | 401 | 401 | Malformed authentication attempt is rejected before business logic |

The authorization row uses a persisted PRIVATE or NAMESPACE_ONLY fixture and
the real read-authorization path:

| Valid credential, insufficient resource permission | `whoami` | `search` | Restricted `resolve` | Restricted latest download | Restricted versioned download |
|---|---:|---:|---:|---:|---:|
| Outsider token with no qualifying namespace role | 200 | 200 with restricted skill omitted | 403 | 403 | 403 |

The same fixture must also prove that an authorized owner or qualifying
namespace member can reach the restricted read path, so a 403 cannot be caused
by an invalid fixture. Missing-scope DELETE coverage is optional supplementary
evidence for the API-token scope filter only.

## Error Handling and Security

- Invalid Bearer credentials return the existing structured HTTP 401 response
  through `ApiAuthenticationEntryPoint`.
- Valid credentials that fail scope or resource authorization return the
  existing structured HTTP 403 response through the access-denied path.
- Responses must not reveal whether a token is unknown, expired, or revoked.
- Tests, logs, documentation, and commits must not contain real secrets. Test
  credentials are generated locally and exist only in the in-memory test
  database.
- Token material must never be logged.

## Documentation

Two documentation updates are required:

1. Update `docs/03-authentication-design.md` so the CLI API section uses the
   current `/api/cli/v1/...` routes and explicitly states Bearer-over-Session
   priority, Session fallback, and the anonymous/401/403 boundary.
2. Add `docs/api/authentication.openapi.yaml` using OpenAPI 3.0. The document
   must define Bearer and Web Session authentication, all affected paths,
   query/path parameters, success schemas, the common response envelope, HTTP
   401 and 403 responses, examples, credential priority, and the rule that
   requests without either identity are allowed only on existing public-read
   routes. `CliWhoAmI.email` remains required but is nullable.

No controller signature or response schema changes are planned. Therefore the
generated `web/src/api/generated/schema.d.ts` should remain unchanged; if a
production fix unexpectedly changes a controller contract, `make generate-api`
becomes mandatory and the generated diff must be committed.

## Implementation Plan Requirements

The detailed implementation plan must preserve the following independent
steps rather than collapsing them into one generic download case:

1. Create the real persisted token/user fixture and public endpoint stubs used
   by the authentication matrix.
2. Exercise `whoami`, `search`, and `resolve` for every credential state.
3. Exercise latest download for every credential state.
4. Exercise versioned download for every credential state.
5. Persist a restricted skill plus authorized and unauthorized users, then use
   the real read-authorization path to prove 403 for restricted `resolve`,
   latest download, and versioned download and success for an authorized user.
6. Update the authentication design and OpenAPI contract.
7. Exercise Session-only, Session + Basic, Basic-only, and Session + valid or
   invalid Bearer independently on all five endpoints; latest and versioned
   download remain separate cases.
8. Prove PRIVATE search omission with a non-empty same-keyword PUBLIC result
   and assert the fixed five-field 403 envelope on each restricted read.
9. Identify the published/running image and replay the valid-to-revoked token
   lifecycle against that exact digest, or record the external access blocker
   without treating the field contradiction as resolved.

Each endpoint/state step must state its own expected status and test command.
The plan may share fixture helpers, but it must not share one assertion in a
way that can skip either download route.

## Verification

Verification proceeds in this order:

1. Run the new focused persisted-token matrix and record whether it fails or
   passes on unmodified `main` behavior, with separate results for latest and
   versioned download.
2. Run the persisted restricted-resource checks through real query/download
   authorization and record outsider 403 plus authorized-user success.
3. If an authentication row fails, preserve the failure output as reproduction
   evidence, apply one minimal shared fix, and rerun the focused matrix.
4. Run auth-module and affected app integration tests.
5. Run `make test-backend-app`.
6. Run `make typecheck-web` and `make lint-web` as repository pre-PR gates.
7. Run `make staging` for containerized regression and smoke coverage.
8. Run `git diff --check` and confirm no generated OpenAPI type drift when no
   controller contract changed.
9. Record the release tag, build revision, image reference, immutable digest,
   and every serving replica's running image identity.
10. Replay the same valid-to-revoked token lifecycle against the identified
    runtime and record endpoint-level status, request ID, and replica evidence,
    keeping latest and versioned download results separate.
11. Perform structured security and code review before updating the existing
    single final pull request.

## Delivery Constraints

- Work only on `fix/auth-revoked-token-validation`.
- Keep PR #511 closed and use it only as historical reference.
- Create exactly one final pull request for GitHub issue #605.
- GitHub-facing text must not contain a Multica issue identifier.
- Do not mark the defect resolved or eligible for closure while the reported
  runtime behavior and the identified artifact/runtime replay remain
  contradictory or incomplete.
- Do not merge `main`; merging remains the responsibility of an explicitly
  authorized human owner.
