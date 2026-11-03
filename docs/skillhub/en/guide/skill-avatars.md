# Skill avatars and moderation

This page explains avatar submission by skill owners and application handling by authorized reviewers. Skill avatars do not include account profile pictures. Image moderation and skill-package security scanning are independent checks. Deployment operators configure services, credentials, and containers through [Configure avatar machine moderation](./avatar-moderation-configuration.html).

## Prerequisites and responsibilities

Sign in to the same registry and identify the target skill and account. Skill ownership controls image maintenance; namespace or platform roles do not automatically permit uploading on another owner's behalf.

| Operation | Responsible actor | Entry point and boundary |
| --- | --- | --- |
| Add, replace, or remove an avatar | Skill owner | Publishing page or My Skills → Update; avatar-only updates do not create versions |
| Review an avatar application | Account with review permission in the target namespace | Existing skill review queue; package and avatar changes submitted together receive a joint decision |
| Change machine and manual switches | Super administrator (`SUPER_ADMIN`) | `/dashboard/admin/system-config`; credentials and provider selection are not configured here |
| Integrate or switch a provider | Operator with deployment permission | Select a provider and inject private credentials through deployment configuration |

Team namespace Owners and Admins review their namespace; platform skill administrators (`SKILL_ADMIN`) and super administrators can review team and global skills. [Skill review rules](./review.html) govern self-review; platform roles must not be interpreted as unrestricted self-review permission. Authorized operators may remove an avatar but cannot upload one for its owner.

## Submit and update an avatar

1. Select a custom PNG/JPEG in the publishing or update form, then adjust the square crop and zoom. Submissions without an image use the skill's initial letter.
2. Confirm the crop and wait for upload completion. Pending or failed uploads prevent submission. The server validates the image and re-encodes it as PNG. A successful upload means private storage succeeded, not that moderation approved it.
3. After submission, inspect the candidate image and its machine/manual states in My Skills or the update page. Until activation, public detail and lists retain the previous avatar or initial letter; that does not indicate a lost submission.
4. After rejection, read the reason, revise the image, and resubmit. A new application supersedes the previous pending candidate; old review results cannot overwrite it. Application records remain available.

Updates retain the existing image by default. Avatar-only removal restores the initial letter after successful submission; removal submitted with a package waits for that application's approval. Cancelling or failing submission leaves the active image unchanged. There is no separate avatar-management entry point.

## Moderation policy and application relationship

An avatar submitted with a package shares one skill application and receives a joint approval or rejection in the existing review detail. It does not activate early. An avatar-only change requiring a human decision enters the same queue, marked as an avatar change, without creating a skill version.

Super administrators manage two switches in System Configuration. Machine moderation defaults off; manual moderation defaults on. Provider credentials are not configured or displayed there. The following matrix applies to avatar-only changes for non-private skills.

| Machine | Manual | Activation condition |
| --- | --- | --- |
| Off | Off | Successful submission |
| Off | On | Manual approval |
| On | Off | Machine pass; blocked or error results do not activate |
| On | On | Machine processing followed by a manual decision |

PRIVATE avatars are owner-readable and used directly without these checks. Expanding visibility requires moderation for the target audience. An avatar submitted with a package waits for the original skill application's approval regardless of avatar switches.

## Machine results and human decisions

| Machine result | Candidate and public image | Next action |
| --- | --- | --- |
| `PASSED` | Waits for manual review when enabled; otherwise activates an avatar-only candidate | Reviewers verify the candidate and target application; a pass does not prove content effectiveness |
| `BLOCKED` | Candidate remains private; the old image remains active | Manual reviewers may reject it; a false-positive override requires an explicit reason. Without manual moderation, the owner may request human review |
| `ERROR` | Candidate remains private and cannot be directly approved | Deployment operators resolve configuration, network, or service failures before retrying |

A machine block is neither an upload failure nor a final human rejection. Owners can inspect rejected candidates and reasons. After manual approval, verify the active image rather than relying only on task text.

## Input, retry, and retention rules

| Item | Fixed value or range | Effect and limitation |
| --- | --- | --- |
| Uploaded image | PNG/JPEG; at most 2MiB; square, at most 1024 pixels per side | Server validation and re-encoding run on upload; out-of-range submissions are rejected |
| Temporary upload reference | 24 hours | Expiry requires another upload; this is not the submitted candidate's retention period |
| Machine-error cooldown | 1 hour | Wait after an error; repeated requests do not bypass the cooldown |
| Automatic calls | 3 total per candidate, including the first | After exhaustion, explicitly retry after cooldown or resubmit |
| Discarded candidate images | Private retention for 30 days after rejection, replacement, or removal | Cleanup removes expired images while textual records remain; deletion is not promised at the exact expiry instant |

Applications retain snapshots of their machine/manual switches and provider. Later switch changes do not rewrite them, and completed machine results are not automatically rerun. If an old candidate still requires its original provider after a switch, processing may return `ERROR`; deployment operators and reviewers must coordinate its handling or use a new application.

Sources: [Upload references](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillAvatarUploadService.java), [image validation](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillAvatarImageProcessor.java), [candidate states](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillAvatarCandidate.java), and [avatar workflow](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillAvatarAppService.java).

## Configure a machine-moderation provider

Each deployment selects one provider and reads credentials at service startup. The administration page only controls moderation switches; saving a switch does not validate cloud permissions, connectivity, or quota. Deployment operators follow [Configure avatar machine moderation](./avatar-moderation-configuration.html) for integration, rotation, and provider changes, then verify actual calls with a new application.

## Completion criteria and troubleshooting

Owners should see the submitted image and status on the update page. Applications requiring review should be identifiable in the existing skill queue. After approval, public detail should display the newly active image. Upload completion, machine pass, human approval, and public-image activation are separate completion criteria.

| Symptom | Likely cause | Diagnostic action | Resolution |
| --- | --- | --- | --- |
| Avatar cannot be submitted | Failed upload, invalid image, or expired reference | Read the upload error and compare input limits and reference time | Crop and upload again; do not change only the extension to bypass validation |
| Public detail retains the old image | New candidate is pending or inactive | Compare the candidate and its machine/manual states in My Skills or Update | Track the current application; do not repeatedly submit the same image instead of querying its state |
| Machine errors persist | Configuration, dependency, or provider failure | Read `ERROR` and retry time; deployment operators inspect service configuration | Retry after recovery and cooldown; do not approve an error as a pass |
| Package still awaits review with manual moderation off | Avatar switches do not control package review | Check whether the application also contains a package and inspect its state | Complete the normal skill review; do not force publication through database changes |
| A suspected false positive cannot activate | Blocked candidate needs human review or an explicit reason | Inspect `BLOCKED`, the manual switch, and review state | The owner requests review; an authorized reviewer evaluates evidence. Do not switch accounts to bypass moderation |
