## Purpose

Give CLI users a bounded, read-only diagnosis of their selected SkillHub registry and registered local installations, with actionable results that preserve existing inventory maintenance and authentication contracts.

## ADDED Requirements

### Requirement: Explicit health mode preserves legacy doctor
The CLI SHALL expose health diagnosis through `doctor --health`. Plain `doctor` and `doctor --json` MUST preserve their inventory reconstruction, output and exit semantics. Health-only options without `--health` MUST fail as usage errors before inventory writes.

#### Scenario: Legacy invocation remains compatible
- **WHEN** a user runs `doctor --json` without `--health`
- **THEN** its existing inventory fields and maintenance behavior remain unchanged

#### Scenario: Health-only option cannot accidentally trigger maintenance
- **WHEN** a user supplies `--offline`, `--registry` or `--token` to doctor without `--health`
- **THEN** the command exits with usage code 5 without writing local state

### Requirement: Diagnostics leave local state unchanged
Health diagnosis MUST NOT create or modify configuration, credentials, inventory, metadata, installed files, backups or installation locks. It SHALL report unreadable or malformed files without echoing their contents. Missing optional state SHALL remain distinguishable from unreadable state.

#### Scenario: Empty home is inspected without initialization
- **WHEN** offline health diagnosis runs against an empty home
- **THEN** no state directories, inventory, credentials or locks are created

#### Scenario: Corrupt or inaccessible state is reported safely
- **WHEN** a required existing state file cannot be read or parsed
- **THEN** a filesystem failure is reported and the original bytes and directory entries remain unchanged

### Requirement: Registry and credential resolution is scoped
Health diagnosis SHALL follow existing registry and token precedence and identify each selected source. It MUST contact only the selected registry, and MUST NOT try stored credentials for another registry. Registry URLs containing credentials, queries, fragments or non-HTTP schemes MUST be rejected without echoing the unsafe input.

#### Scenario: Explicit registry wins without crossing credentials
- **WHEN** an explicit registry overrides environment and saved configuration
- **THEN** only that registry is contacted using its resolved credential

#### Scenario: Configuration failure does not cause an unintended default probe
- **WHEN** saved configuration is unreadable and no registry override is present
- **THEN** online checks are skipped rather than contacting the public default registry

### Requirement: Offline mode has no network side effects
`doctor --health --offline` SHALL perform local checks only and SHALL mark remote connectivity and identity checks as skipped. It MUST NOT make DNS, HTTP, login, update-discovery or browser-opening requests.

#### Scenario: Offline run with a saved token
- **WHEN** offline diagnosis finds a saved credential
- **THEN** its presence and source are reported without a network call or a claim that the credential is valid

### Requirement: Online checks are bounded and classified
Online diagnosis SHALL reuse the existing CLI identity endpoint with one bounded request to the selected registry. It SHALL distinguish DNS, connection, TLS, timeout, HTTP and invalid-response failures when evidence supports the distinction. It MUST NOT forward credentials through redirects or echo raw server bodies and exception messages.

#### Scenario: Network or server failure produces actionable checks
- **WHEN** the registry is unreachable, times out, returns 5xx or returns an invalid identity response
- **THEN** the report identifies the observed failure category and a recovery suggestion without claiming successful identity verification

#### Scenario: Redirect does not leak the credential
- **WHEN** the identity endpoint redirects to another address
- **THEN** the diagnostic request is not followed and the destination receives no credential

### Requirement: Authentication is distinct from anonymous usability and authorization
A rejected selected credential SHALL produce an authentication failure without deletion or automatic login. Missing credentials SHALL be a warning, not a failure of anonymous usability. A 403 SHALL be reported as access denied, not proof that a token expired. A successful identity response MUST NOT imply permission to publish, delete or administer a namespace.

#### Scenario: Rejected credential remains recoverable
- **WHEN** the selected saved credential receives 401
- **THEN** the command reports rejection, suggests explicit login recovery and leaves all credentials unchanged

#### Scenario: Anonymous registry use remains possible
- **WHEN** no credential exists and the identity endpoint returns 401
- **THEN** HTTP connectivity is reported separately and missing login remains a warning

### Requirement: Registered installation findings preserve integrity evidence
Diagnosis SHALL inspect only installations registered for the selected registry, using existing metadata and snapshot rules. Content changes SHALL be warnings, without repair or upgrade. Missing integrity baselines SHALL be reported as unverified; unsafe paths, missing directories or invalid metadata MUST NOT be reported as healthy. Enabled and disabled installations SHALL retain their state.

#### Scenario: Locally modified content is preserved
- **WHEN** a registered installation differs from its saved fingerprint
- **THEN** the report warns that it is modified and leaves its bytes and state unchanged

#### Scenario: Old installation lacks a fingerprint
- **WHEN** a legacy installation has no integrity baseline
- **THEN** diagnosis reports that integrity could not be verified without synthesizing or persisting a fingerprint

### Requirement: Health output and exit codes are stable and safe
Health JSON SHALL be one stdout object with `schemaVersion`, `mode`, `ok`, selected registry/source, credential source, resolved state paths, `checks` and `exitCode`. Each check SHALL have a stable identifier, status, code, message and optional next step. Secrets MUST NOT appear in human or JSON output. Exit 0 covers pass/warning/skipped-only reports; failures use existing codes: filesystem 4, authentication 2, network 3, otherwise 1, in that priority order. Usage remains 5.

#### Scenario: Failed health check is still machine-readable
- **WHEN** one or more required checks fail in JSON mode
- **THEN** stdout contains the complete parseable health report, stderr contains no diagnostic chatter, and the process returns the documented nonzero exit code

#### Scenario: Untrusted failure payload contains credentials
- **WHEN** a registry error body reflects a token or includes private details
- **THEN** neither the body nor the token is included in the diagnostic report
