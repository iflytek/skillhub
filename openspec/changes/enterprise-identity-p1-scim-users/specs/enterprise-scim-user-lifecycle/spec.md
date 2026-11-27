## ADDED Requirements

### Requirement: Directory connection is separate from authentication

The system SHALL model SCIM provisioning as a Directory Connection scoped to one Organization and SHALL NOT reuse a Login Connection for SCIM credentials, lifecycle, or authorization.

#### Scenario: Authentication connection cannot be used as SCIM connection

- **WHEN** a caller supplies a Login Connection identifier to a SCIM endpoint
- **THEN** the request is rejected without reading authentication secrets or mutating memberships.

### Requirement: Organization-scoped SCIM Users

The system SHALL expose SCIM 2.0 User resources only within the Organization bound to the authenticated Directory Connection.

#### Scenario: Cross-organization resource access

- **WHEN** a valid directory token for Organization A requests a resource belonging to Organization B
- **THEN** the system returns a standard not-found or forbidden SCIM error without revealing whether the resource exists.

### Requirement: Stable external resource identity

The system SHALL identify a directory user by the Directory Connection and stable upstream resource identifier. Email and userName SHALL NOT be the sole identity key.

#### Scenario: Email address changes

- **WHEN** the upstream system sends the same external resource identifier with a new email address
- **THEN** the existing directory membership is updated and no second membership or platform account is created.

### Requirement: Idempotent user lifecycle writes

The system SHALL support SCIM create, replace, patch, and deprovision semantics with idempotent replay and stale-update protection.

#### Scenario: Repeated create

- **WHEN** the same create request is delivered more than once for the same directory resource
- **THEN** the first membership is retained and subsequent requests return the current resource without creating duplicates.

#### Scenario: Stale update

- **WHEN** an older update arrives after a newer source version has been applied
- **THEN** the older update cannot restore stale profile or active state and the current resource remains authoritative.

### Requirement: Directory-owned membership status

The system SHALL map SCIM active/deprovisioned state to a DIRECTORY-sourced OrganizationMembership and SHALL preserve unrelated manual membership and Namespace authorization.

#### Scenario: Directory deactivation

- **WHEN** a directory user is deactivated or deleted
- **THEN** the directory membership becomes suspended or deprovisioned, authority version increases, existing enterprise sessions are rejected, and unrelated manual membership and Namespace grants remain.

### Requirement: No implicit account or entitlement grant

SCIM provisioning SHALL NOT create public login bindings, grant organization roles, or project users into Namespace entitlements solely because a User resource was provisioned.

#### Scenario: Provisioned user before first login

- **WHEN** SCIM creates a user before that person completes enterprise login
- **THEN** the membership may remain PROVISIONED without a platform user id and no Namespace or administrative role is granted.

### Requirement: Secrets and payloads are protected

The system SHALL never expose directory bearer secrets, passwords, tokens, or raw SCIM request bodies in API responses, UI, audit records, or logs.

#### Scenario: Secret rotation

- **WHEN** an authorized secret administrator rotates a Directory Connection secret
- **THEN** the old secret follows the configured overlap/revocation policy, the new value is write-only, and responses contain only masked metadata.
