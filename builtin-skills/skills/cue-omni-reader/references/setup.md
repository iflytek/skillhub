# Cue Omni Reader setup

The SkillHub-reviewed Bridge is `@cueai/omni-reader-mcp@1.8.0` and requires Node.js 20.12 or newer.
It uses `CUE_API_KEY`, obtained by the user from <https://cuecue.cn/hub/api-key> and configured only
through the Agent's secure environment or local secret facility.

## Before setup

Explain the external processing boundary, the MCP configuration change, and any local directory to
be authorized. Obtain confirmation, then grant only the minimum absolute directory. Do not place a
credential in chat, commands, logs, Skill files, or generated JSON. If a key was exposed, stop and
ask the user to rotate it.

Install the audited version only after approval:

```sh
npx -y @cueai/omni-reader-mcp@1.8.0 setup
```

The interactive setup has native configuration for Hermes, Cursor, and Claude Desktop. For another
client, choose **Other** and apply the printed stdio entry using that client's documented MCP
configuration mechanism. Do not guess a configuration path or claim an unverified adapter.

For an already approved non-interactive setup, supported native examples are:

```sh
npx -y @cueai/omni-reader-mcp@1.8.0 setup --client hermes --allowed-root /absolute/minimum/root --yes --json
npx -y @cueai/omni-reader-mcp@1.8.0 setup --client cursor --add-root /absolute/minimum/root --yes --json
npx -y @cueai/omni-reader-mcp@1.8.0 setup --client claude-desktop --allowed-root /absolute/minimum/root --yes --json
```

`--allowed-root` replaces the explicit additional-root set; `--add-root` appends one root. Both
require an absolute path and cannot be combined. On macOS/Linux, `OMNI_ALLOWED_ROOTS` separates
multiple roots with `:`; on Windows it uses `;`. The current workspace remains the default allowed
area.

Verify after setup or a root change:

```sh
npx -y @cueai/omni-reader-mcp@1.8.0 doctor --json
```

`doctor` must not expose the API key, a private source path, or source content. Reconnect the MCP
server so it receives the configuration, then verify `parse`, `get_parse_status`, `cancel_parse`,
`read_result`, `read_outline`, `discard_result`, and `save_result` are visible. Only a real,
authorized local-file parse proves the data path end to end.

Do not run `doctor --silent-check`, query npm `latest`, or upgrade automatically. Bridge upgrades
must be reviewed and released as a new SkillHub package.

To remove only a trusted managed entry after explicit approval:

```sh
npx -y @cueai/omni-reader-mcp@1.8.0 uninstall --yes --json
```

Uninstall does not delete user sources or silently discard unexpired results. Recover an existing
operation before replacement work.
