# Hermes Tweet Skill Example

This example documents a Hermes Tweet package flow for teams using SkillHub as their internal agent skill registry.

Hermes Tweet is a Hermes Agent skill for X/Twitter research, reading, and gated action workflows. The action tool remains disabled unless the operator explicitly enables it.

## Package Source

Use the published Hermes Tweet repository as the source package:

```bash
git clone https://github.com/Xquik-dev/hermes-tweet.git
cd hermes-tweet
```

The packaged skill directory is:

```text
hermes_tweet/skills/hermes-tweet/
```

## Publish With The CLI

After signing in to a SkillHub registry, publish the package with descriptive tags:

```bash
skillhub publish ./hermes_tweet/skills/hermes-tweet \
  --name hermes-tweet \
  --version 0.1.6 \
  --description "Hermes Agent skill for X/Twitter research, reading, and gated actions" \
  --tags hermes,x-twitter,social-media,automation
```

## Install For Hermes Agents

Search first, then install into the target agent profile:

```bash
skillhub search hermes-tweet
skillhub install hermes-tweet --agent hermes
```

## Required Runtime Variables

Do not store raw secrets in SkillHub docs or repository files.

| Name | Purpose |
| --- | --- |
| `XQUIK_API_KEY` | Enables live read tools. |
| `HERMES_TWEET_ENABLE_ACTIONS` | Enables action tools only when set to `true`. |

Keep action access disabled by default for research-only agents.
