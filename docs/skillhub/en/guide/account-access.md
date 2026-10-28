# Sign In and Manage Access Tokens

This guide covers web login, CLI authorization, and API tokens for scripts. Browser sessions, stored CLI credentials, and third-party credentials used by a skill are separate objects. Creating a SkillHub token does not inject external service credentials into skills.

## Choose a sign-in method

Open `/login` on your instance and use an available method:

| Method | Prerequisite | Successful outcome |
| --- | --- | --- |
| Username and password | Local password login is enabled and you have an account | A browser session is established |
| Self-registration | The instance allows registration | Submit username, password, and email; success establishes a session |
| Organization or external login | The provider is configured on the instance | Complete external authentication and return to SkillHub |

A missing registration button is not necessarily a UI failure. The page loads local authentication capabilities and available external methods. Disabling password login or self-registration changes the available entry points. Provider extensibility does not mean every provider is configured.

Successful login does not grant publishing or administration rights. Account status, platform roles, namespace membership, and resource visibility still govern operations; see [namespace management](./namespace.md).

## Authorize the CLI

Select the intended instance before logging in:

```bash
skillhub login --registry https://skillhub.example.com
skillhub whoami --registry https://skillhub.example.com
```

`skillhub login` first resolves a token from command-line arguments, environment variables, or stored credentials for the registry, in that order. Device authorization starts only when none is found. An invalid resolved token does not automatically fall back to device authorization. Follow the verification URL and code in the terminal and approve in the browser. On a remote terminal, use `--no-open` and open the URL yourself. Verify the resulting identity with `whoami`.

Credentials are stored per registry. Changing `--registry` or `SKILLHUB_REGISTRY` does not authenticate you on another instance. Signing into the website alone also does not populate CLI credentials.

For automation, supply an API token through your runtime's secret-management mechanism. Do not put its real value in skill packages, project source, screenshots, or shared logs. See [CLI reference](./cli.md) for credential precedence.

## Create an API token

1. Sign into the website and open `/dashboard/tokens`.
2. Create a token with a recognizable purpose-specific name. The UI checks empty names, names longer than 64 characters, and duplicate names visible on the current list page.
3. Choose no expiry, 7 days, 30 days, 90 days, or a custom expiration. A custom value must be valid and in the future.
4. Copy and securely store the full value immediately after creation. Once the creation result is closed, the list provides summaries such as name, prefix, creation time, expiry, and last use—not the full value again.
5. Configure the target client and use `skillhub whoami` to verify the registry and identity.

A token acts on behalf of its owner; it does not bypass resource permissions. The creation dialog does not grant arbitrary platform roles.

## Change expiry or revoke a token

Use the token list to change its expiration or revoke a selected token. Check the updated expiry in the list. Clients using a revoked token must replace their credentials.

If the full value is lost, do not try to reconstruct it from its prefix. Create a replacement with a new unique name, update clients, and revoke the old token when no longer needed. Server-side creation under an existing name rotates that token and revokes the active predecessor first; the current-page duplicate-name warning cannot guarantee that old credentials remain usable. Revoke suspected compromised credentials promptly and investigate their use.

```bash
# Remove locally stored CLI credentials for this registry only
skillhub logout --registry https://skillhub.example.com
```

`logout` does not revoke all server-side API tokens or remove installed skills. A token supplied through an environment variable may still authenticate subsequent commands; check all credential sources when troubleshooting.

## Password reset versus password change

For a forgotten password, follow the login page to `/reset-password`, request a code for the account email, and submit the email, code, and new password. This page depends on instance email configuration. A successful request response does not guarantee email delivery or prove that an address is registered.

The separate `/api/v1/auth/local/change-password` endpoint requires the current authenticated identity and current password. It is not the email-code reset flow; do not assume every password change requires email verification. Manage an external identity provider's password through that provider.

`BOOTSTRAP_ADMIN_PASSWORD` initializes credentials; it does not reset an existing administrator password. See the [SMTP guide](https://github.com/iflytek/skillhub/blob/main/docs/19-smtp-password-reset-email-setup.md).

## Troubleshooting

| Symptom | Check in order |
| --- | --- |
| Website signed in but CLI unauthenticated | Confirm the registry, then CLI login and `whoami`; browser cookies are not CLI tokens |
| CLI uses the wrong account after login | Check command-line token, `SKILLHUB_TOKEN`, and stored per-registry credentials |
| Token cannot call an endpoint | Check expiry/revocation, account/resource permissions, and token scope; retain the public error and `requestId` |
| Reset email not received | Check the email address, spam folder, instance SMTP configuration, and logs; do not repeatedly request codes to bypass throttling |
| Login returns to the login page | Inspect subsequent identity requests, cookies, proxy, and session configuration; do not clear the database |

When requesting help, provide the instance version, entry point, public error, and `requestId`. Do not share full tokens, verification codes, passwords, or cookies.
