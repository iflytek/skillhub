# Manage Namespaces

This page covers team namespace creation, membership maintenance and lifecycle governance for authorized operators. Skill ownership, platform roles and namespace roles are managed separately. Creating or managing a namespace does not grant a platform role. Lifecycle actions on this page do not apply to `GLOBAL` or other non-team types.

## Prerequisites and Responsibilities

A namespace supplies the ownership segment of a Skill coordinate, such as `@team/example-skill`. This is an example coordinate, not a guaranteed available resource. The namespace slug is immutable after creation; authorized operators can change its display name.

| Operation | Required identity | Entry and decision basis |
| --- | --- | --- |
| Create a team namespace | Platform Skill administrator (`SKILL_ADMIN`) or super administrator (`SUPER_ADMIN`) | Creation entry at `/dashboard/namespaces`; ordinary members cannot create one |
| Maintain members and settings | Namespace Owner or Admin | Namespace is `ACTIVE` and the detail page exposes the action |
| Publish Skills | Member or another authorized publisher | Select the namespace in the publishing form; scanning, review and Skill maintenance permissions still apply |
| View members | Existing namespace member | Members tab; read access does not include membership mutation |

Sources: [creation service](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/NamespacePortalCommandAppService.java) and [namespace policy](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceAccessPolicy.java).

## Create a Namespace and Establish Collaboration

1. An authorized creator opens the creation entry and supplies a display name, stable slug and purpose. The creator becomes the namespace Owner; this action does not add a platform role.
2. Open Members in the namespace detail page and add an existing account or create an invitation. Select `MEMBER` or `ADMIN` when adding members. Use the separate ownership transfer entry for the namespace Owner; the ordinary role selector does not assign `OWNER`.
3. Members select this namespace when publishing. Select namespace-member visibility (`NAMESPACE_ONLY`) for member sharing. Private (`PRIVATE`) content is not available to all members; public (`PUBLIC`) content does not require namespace membership for reading.
4. An authorized reviewer handles the submission from the namespace review entry. The author follows My Review Progress. Membership does not prove publication or review completion.

![Responsibilities from namespace creation and member acceptance to publishing and review](/diagrams/namespace-membership-flow-en.svg)

Figure: Creation, membership acceptance and publishing are separate actions. An invitation grants membership, not publication.

The following creation and membership requests use placeholder names, slugs and user identifiers. Programmatic callers must also satisfy the instance authentication and CSRF requirements.

```http
POST /api/v1/namespaces
Content-Type: application/json

{"displayName":"Example Team","slug":"example-team","description":"Shared authoring"}
```

```http
POST /api/v1/namespaces/{slug}/members
Content-Type: application/json

{"userId":"example-user","role":"MEMBER"}
```

## Invitations, Role Changes and History

Create an invitation under Members → Invite Members in an `ACTIVE` team namespace, then share it through a controlled channel. The holder must complete login and existing admission checks, verify the target, account and `MEMBER` role, and confirm acceptance. Opening the link does not create membership. Links are not restricted to a particular email or account and must not be published in public channels.

| Item | Current rule | Effect and boundary |
| --- | --- | --- |
| Lifetime and use count | 24 hours from creation; one new member can use it once | Fixed rule; expiration or use prevents further joining |
| Replacement and revocation | Confirmation immediately invalidates the old link | Existing members retain membership |
| Web invitation history | Five entries per page, with pagination | Fixed web presentation; full account identifier is available on hover |
| History API pagination | 1–50 entries per page | Controlled by request parameters; no invitation code or hash is returned |
| History retention | Retained while the namespace exists | Permanent namespace deletion removes invitation business records; operation audit follows its existing retention rules |

Links use `/join#invite=invitation-code`. The creator's account in the same browser tab temporarily retains the link. After a refresh, reopening the dialog validates that the link is still active before enabling copying. Another account, tab or device cannot recover it from this temporary storage. History does not expose the original code. If the original link is lost, creating a new invitation requires confirming replacement. Login redirects do not contain the invitation Token.

History displays the creator, creation and expiration times, status, and accepted account. Account renaming or membership removal does not rewrite the original joining fact; history is not a current membership list. Only namespace Owners and Admins can read it; frozen or archived namespaces expose read-only history. Upgrades migrate only a verifiable current invitation and mark it as a legacy record; earlier history is not inferred.

To change a role, select the member-row role edit action and save from the actions column, then re-read the role. Member removal and namespace ownership transfer require separate confirmation. Transferring a namespace does not transfer its Skills. Sources: [invitation service](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceInvitationService.java) and [history presentation](https://github.com/iflytek/skillhub/blob/main/web/src/features/namespace/invitation-history.tsx).

## Lifecycle Governance

The following matrices apply to `TEAM`. Server permissions and dependency checks take precedence over role names.

| Namespace state | Namespace role | Available actions | Restrictions |
| --- | --- | --- | --- |
| `ACTIVE` | Owner | Edit settings, manage members, transfer ownership, freeze, archive, delete | Deletion also requires no dependencies |
| `ACTIVE` | Admin | Edit settings, manage members, freeze | Cannot archive, delete or transfer ownership |
| `ACTIVE` | Member | None of these governance actions | Skill ownership does not add namespace governance permission |
| `FROZEN` | Owner | Unfreeze, archive, delete | Membership management is read-only; deletion also requires no dependencies |
| `FROZEN` | Admin | Unfreeze | Cannot archive or delete |
| `FROZEN` | Member | None of these governance actions | No writes |
| `ARCHIVED` | Owner | Restore to `ACTIVE`, delete | Deletion also requires no dependencies |
| `ARCHIVED` | Admin or Member | None of these governance actions | Previous edit permission does not grant restoration |

| Namespace state | Platform identity | Dedicated platform governance entry | Restrictions |
| --- | --- | --- | --- |
| `ACTIVE` | Super administrator | Freeze, archive | Membership is not required; no permanent deletion entry |
| `FROZEN` | Super administrator | Unfreeze, archive | Membership is not required |
| `ARCHIVED` | Super administrator | Restore to `ACTIVE` | Membership is not required |
| Any | Skill administrator or user administrator | These platform roles alone grant none of the dedicated actions above | Apply the previous table if the account also has a namespace role |

Freezing pauses writes; it does not retrieve files already installed on clients. Existing versions remain readable under their access rules. Archiving removes the namespace from search while preserving existing Skill access. Permanent deletion is Owner-only and requires no Skills, review tasks, promotion requests targeting the namespace, or authoring drafts. Archiving does not remove these dependencies.

Sources: [namespace service](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceService.java) and [platform governance controller](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminNamespaceController.java).

## Completion Criteria and Failure Handling

After creation, the list must show the target slug, `ACTIVE`, and the creator's Owner role. After acceptance, the member list must include the actual account with `MEMBER`. Re-read namespace details after governance actions and verify the target state, such as `ACTIVE` after unfreezing. A used invitation record does not prove that its account is still a member.

| Symptom | Likely cause | Diagnosis | Resolution |
| --- | --- | --- | --- |
| Creation entry is absent | Required platform role is missing | Compare platform roles with the current page identity | Contact an authorized creator; do not repeatedly register accounts |
| Invitation cannot be accepted | Expired, used, revoked or namespace is not writable | Read invitation status in the dialog and namespace status in Settings | The original operator creates a new invitation if required; do not reuse the invalid link |
| Link cannot be copied after refresh | Temporary tab storage is unavailable or the link is invalid | Reopen the dialog in the original account and tab; read validation result | Retry validation first; confirm replacement if the code cannot be recovered; do not treat history as an invitation code |
| Member actions are unavailable | Account is not Owner/Admin or namespace is frozen/archived | Check member role, state and read-only message | An authorized operator restores the namespace if appropriate; do not modify browser requests to bypass permission |
| Permanent deletion is rejected | One of the four dependency types remains | Follow the response to Skills, reviews, promotions or drafts | Handle each resource through its own workflow first; do not delete database records directly |

## Membership Removal and Maintenance Boundaries

Removing a member does not delete, move or transfer their Skills. Published content retains its original visibility. The current Skill owner with an active account can still view and download their own content, including private content, unpublished versions and historical versions, from My Skills; the page displays a read-only message. Upload, republishing, avatar or visibility changes, withdrawal, archiving and deletion are unavailable on the basis of that ownership alone. This exception does not expose other members' restricted content.

Rejoining restores maintenance under the original rules if ownership is unchanged. Disabled accounts cannot act; re-enabling an account does not restore removed membership. Existing submissions continue normal review and are not automatically approved or withdrawn. Existing namespace and platform governance permissions remain unchanged. This version does not add Skill transfer, self-service departure or permanent account deletion. The [owner access policy](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillOwnerAccessPolicy.java) does not replace other management permissions.

## Further Reading

- [Publish and Update Skills](./skill-publish.html): Submit versions for the intended visibility.
- [Review Skills and Suites](./review.html): Identify pending objects and their results.
- [Accounts and Access Tokens](./account-access.html): Separate login identities from API credentials.
